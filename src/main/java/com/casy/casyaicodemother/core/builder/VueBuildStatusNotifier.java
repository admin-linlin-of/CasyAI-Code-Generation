package com.casy.casyaicodemother.core.builder;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.model.enums.VersionBuildStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vue 项目打包状态的 SSE 推送中心。
 * <p>
 * 背景：传统模式下打包由 {@link VueProjectBuilder#buildProjectAsync} 在虚拟线程中异步执行，
 * 前端原先通过轮询 {@code GET /tAppVersion/list/{appId}} 查询 {@code build_status}，间隔 3 秒、
 * 最长约 6 分钟，产生大量无效 HTTP 请求。
 * <p>
 * 改造后流程：
 * <pre>
 * 1. 前端建立 EventSource → GET /tAppVersion/build/stream
 * 2. AppVersionServiceImpl#buildVersionStream 调用 subscribe 注册 SseEmitter
 * 3. VueProjectBuilder 异步 build 过程中，updateBuildStatus 写入 DB 后调用 publish
 * 4. publish 向所有订阅者推送 event=build_status；终态（success/failed）再推 event=done 并关闭连接
 * </pre>
 * <p>
 * 线程安全：{@code emitters} 使用 ConcurrentHashMap + CopyOnWriteArrayList，
 * 支持同一 appId+codeDir 多个浏览器 Tab 同时订阅。
 */
@Slf4j
@Component
public class VueBuildStatusNotifier {

    /** SSE 连接最长存活时间，与 npm install/build 超时（300s×2）及前端兜底超时对齐 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    /**
     * 订阅表：key = {@code appId:codeDir}，value = 该版本所有活跃的 SseEmitter。
     * 终态推送完成后会 remove 整个 key，避免内存泄漏。
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 注册一个新的 SSE 订阅者。
     * <p>
     * 调用方（{@link com.casy.casyaicodemother.service.impl.AppVersionServiceImpl#buildVersionStream}）
     * 应在注册后立即返回 emitter 给 Spring MVC，由容器持有连接；
     * 后续 {@link #publish} 或 {@link #sendImmediate} 负责向该连接写事件。
     *
     * @param appId   应用 ID
     * @param codeDir 版本目录，如 v1
     * @return 已注册回调（completion/timeout/error 自动清理）的 SseEmitter
     */
    public SseEmitter subscribe(Long appId, String codeDir) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String key = key(appId, codeDir);
        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(ex -> remove(key, emitter));
        return emitter;
    }

    /**
     * 广播打包状态变更（由 {@code updateBuildStatus} 在 DB 更新成功后调用）。
     * <p>
     * 若当前无任何订阅者（例如 build 在 SSE 连接建立前就已结束），则静默跳过，
     * 此时 {@link com.casy.casyaicodemother.service.impl.AppVersionServiceImpl#buildVersionStream}
     * 会通过读 DB 当前状态 + {@link #sendImmediate} 补偿。
     *
     * @param appId      应用 ID
     * @param codeDir    版本目录
     * @param status     构建状态枚举
     * @param buildError 失败时的 npm 输出摘要，成功/构建中传 null
     */
    public void publish(Long appId, String codeDir, VersionBuildStatusEnum status, String buildError) {
        if (appId == null || StrUtil.isBlank(codeDir) || status == null) {
            return;
        }
        String key = key(appId, codeDir);
        List<SseEmitter> listeners = emitters.get(key);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : listeners) {
            try {
                sendStatus(emitter, status, buildError);
                if (isTerminal(status)) {
                    emitter.send(SseEmitter.event().name("done").data(""));
                    emitter.complete();
                }
            } catch (Exception ex) {
                log.debug("推送打包状态失败 appId={} codeDir={}: {}", appId, codeDir, ex.getMessage());
                remove(key, emitter);
            }
        }
        if (isTerminal(status)) {
            emitters.remove(key);
        }
    }

    /**
     * 向单个订阅者推送「当前已知状态」，用于 SSE 连接刚建立、尚未触发新 build 的场景。
     * <p>
     * 典型用例：
     * <ul>
     *   <li>工作流模式 {@code skipIfSuccess=true}：ProjectBuilderNode 已同步 build 成功，直接推 success</li>
     *   <li>版本已在 building：另一 Tab 触发了 build，本连接只订阅进度</li>
     *   <li>工作流 build 失败且 {@code skipIfSuccess=true}：直接推 failed + buildError</li>
     * </ul>
     * 在虚拟线程中调用，避免阻塞 Servlet 线程。
     */
    public void sendImmediate(SseEmitter emitter, VersionBuildStatusEnum status, String buildError) {
        if (emitter == null || status == null) {
            return;
        }
        try {
            sendStatus(emitter, status, buildError);
            if (isTerminal(status)) {
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            }
        } catch (IOException ex) {
            log.debug("推送即时打包状态失败: {}", ex.getMessage());
            emitter.completeWithError(ex);
        }
    }

    /**
     * 发送单条 build_status 事件。
     * <p>
     * SSE 格式：
     * <pre>
     * event: build_status
     * data: {"status":"building"}                    // 或 success / failed
     * data: {"status":"failed","buildError":"..."}   // 失败时附带截断后的 npm 日志
     * </pre>
     */
    private void sendStatus(SseEmitter emitter, VersionBuildStatusEnum status, String buildError) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status.getValue());
        if (StrUtil.isNotBlank(buildError)) {
            payload.put("buildError", buildError);
        }
        emitter.send(SseEmitter.event().name("build_status").data(JSONUtil.toJsonStr(payload)));
    }

    /** success / failed 为终态，推送后应发送 done 并关闭 SSE */
    private static boolean isTerminal(VersionBuildStatusEnum status) {
        return status == VersionBuildStatusEnum.SUCCESS || status == VersionBuildStatusEnum.FAILED;
    }

    private static String key(Long appId, String codeDir) {
        return appId + ":" + codeDir;
    }

    /** 连接关闭/超时/出错时从订阅表移除，防止 SseEmitter 泄漏 */
    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> listeners = emitters.get(key);
        if (listeners == null) {
            return;
        }
        listeners.remove(emitter);
        if (listeners.isEmpty()) {
            emitters.remove(key);
        }
    }
}

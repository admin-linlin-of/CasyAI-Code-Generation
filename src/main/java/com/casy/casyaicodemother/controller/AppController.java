package com.casy.casyaicodemother.controller;

import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.service.AppService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应用 控制层。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@RestController
@RequestMapping("/tApp")
public class AppController {

    @Resource
    private AppService AppService;

    /**
     * 保存应用。
     *
     * @param app 应用
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody App app) {
        return AppService.save(app);
    }

    /**
     * 根据主键删除应用。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable Long id) {
        return AppService.removeById(id);
    }

    /**
     * 根据主键更新应用。
     *
     * @param app 应用
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public boolean update(@RequestBody App app) {
        return AppService.updateById(app);
    }

    /**
     * 查询所有应用。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<App> list() {
        return AppService.list();
    }

    /**
     * 根据主键获取应用。
     *
     * @param id 应用主键
     * @return 应用详情
     */
    @GetMapping("getInfo/{id}")
    public App getInfo(@PathVariable Long id) {
        return AppService.getById(id);
    }

    /**
     * 分页查询应用。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<App> page(Page<App> page) {
        return AppService.page(page);
    }

}

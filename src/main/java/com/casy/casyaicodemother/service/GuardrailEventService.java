package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.entity.GuardrailEvent;
import com.casy.casyaicodemother.model.entity.User;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

public interface GuardrailEventService extends IService<GuardrailEvent> {

    void record(Throwable error, User loginUser, Long appId, HttpServletRequest request, String handleResult);
}

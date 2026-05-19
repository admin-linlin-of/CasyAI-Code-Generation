package com.casy.casyaicodemother.service.impl;

import com.casy.casyaicodemother.mapper.AppMapper;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.service.AppService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService {

}

package com.casy.casyaicodemother.model.vo.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class AppCodeFileListVO implements Serializable {

    private List<String> files;

    @Serial
    private static final long serialVersionUID = 1L;
}

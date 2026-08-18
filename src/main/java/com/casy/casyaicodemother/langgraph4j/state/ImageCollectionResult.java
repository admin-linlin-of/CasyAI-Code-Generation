package com.casy.casyaicodemother.langgraph4j.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageCollectionResult implements Serializable {

    private List<ImageResource> values;

    @Serial
    private static final long serialVersionUID = 1L;
}

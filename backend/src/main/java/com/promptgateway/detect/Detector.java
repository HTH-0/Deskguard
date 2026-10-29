package com.promptgateway.detect;

import java.util.List;

/** 문장에서 한 종류 이상의 항목을 찾는다. 외부 서비스를 부르지 않는다(NFR-4). */
public interface Detector {
    List<Finding> detect(String text);
}

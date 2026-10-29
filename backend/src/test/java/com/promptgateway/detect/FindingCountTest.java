package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class FindingCountTest {

    @Test
    @DisplayName("탐지를 항목별 개수로 묶는다(범주 순, 같은 범주는 항목 순). 값과 위치는 버린다")
    void groupsByItem() {
        List<Finding> findings = List.of(new Finding(Item.PHONE, 0, 1, "a"), new Finding(Item.CLIENT, 2, 3, "b"),
                new Finding(Item.PHONE, 4, 5, "c"), new Finding(Item.PASSWORD, 6, 7, "d"));

        assertThat(FindingCount.of(findings)).extracting(FindingCount::category, FindingCount::item, FindingCount::count)
                .containsExactly(tuple(Category.D1, Item.PHONE, 2), tuple(Category.D2, Item.PASSWORD, 1), tuple(Category.D3, Item.CLIENT, 1));
    }
}

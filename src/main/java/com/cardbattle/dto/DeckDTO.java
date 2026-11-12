package com.cardbattle.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class DeckDTO {
    private Long id;                       // 编辑已有卡组时传
    @NotBlank(message = "卡组名不能为空")
    private String name;
    @Size(min = 30, max = 30, message = "卡组必须恰好30张")
    private List<Long> cardIds;
}

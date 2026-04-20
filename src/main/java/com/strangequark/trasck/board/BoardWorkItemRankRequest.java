package com.strangequark.trasck.board;

import java.util.UUID;

public record BoardWorkItemRankRequest(
        UUID previousWorkItemId,
        UUID nextWorkItemId
) {
}

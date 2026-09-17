package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaxCommandSupportTest {

    @Test
    void startAndUnblockRemainCaseInsensitiveButUntrimmed() {
        assertThat(MaxCommandSupport.isStartCommand("/START")).isTrue();
        assertThat(MaxCommandSupport.isUnblockCommand("/UnBlOcK")).isTrue();
        assertThat(MaxCommandSupport.isStartCommand(" /start ")).isFalse();
        assertThat(MaxCommandSupport.isUnblockCommand(" /unblock ")).isFalse();
    }

    @Test
    void cancelPreservesTrimmedCaseInsensitiveAliases() {
        assertThat(MaxCommandSupport.isCancelCommand(" /CANCEL ")).isTrue();
        assertThat(MaxCommandSupport.isCancelCommand(" cancel ")).isTrue();
        assertThat(MaxCommandSupport.isCancelCommand(" ОтМеНа ")).isTrue();
    }

    @Test
    void nullAndUnrelatedTextAreNotCommands() {
        assertThat(MaxCommandSupport.isStartCommand(null)).isFalse();
        assertThat(MaxCommandSupport.isUnblockCommand(null)).isFalse();
        assertThat(MaxCommandSupport.isCancelCommand(null)).isFalse();
        assertThat(MaxCommandSupport.isCancelCommand("stop")).isFalse();
    }
}

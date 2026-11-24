package com.atlas.agent_service.llm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentConversationTest {
    @Test
    void adviceHasAReplyAndNoFileChanges() {
        var reply = AgentConversation.parse("{\"message\":\"1. Cache results.\\n2. Batch requests.\",\"diff\":null}", 10, "openai");
        assertNull(reply.diff());
        assertTrue(reply.message().contains("2. Batch requests."));
    }

    @Test
    void fileEditsKeepBothTheExplanationAndNormalizedPatch() {
        var reply = AgentConversation.parse("```json\n{\"message\":\"Added hello.py\",\"diff\":\"--- /dev/null\\n+++ b/hello.py\\n@@ -0,0 +1 @@\\n+print('hello')\"}\n```", 10, "openai");
        assertEquals("Added hello.py", reply.message());
        assertTrue(reply.diff().endsWith("\n"));
        assertThrows(LlmException.class, () -> AgentConversation.parse("{\"message\":\"Cut off", 10, "openai"));
        assertThrows(LlmException.class, () -> AgentConversation.parse("{\"message\":\"OK\",\"diff\":7}", 10, "openai"));
    }
}

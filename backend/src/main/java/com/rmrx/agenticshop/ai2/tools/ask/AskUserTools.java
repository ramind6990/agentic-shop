package com.rmrx.agenticshop.ai2.tools.ask;

import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.AskUser;
import com.rmrx.agenticshop.ai2.tools.Sensitive;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The ask tools: the model calls them to ask the user something. They are never executed; the ask-user node pauses
 * the run on each call and turns the user's answer into the call's result. Their arguments are read back with
 * {@link AskChoiceArgs}, {@link AskInputArgs} and {@link AskYesNoArgs}, whose field names match these parameters.
 * <p>
 * {@code ask_yes_no} is only for confirming understanding ("Did you mean order #WE45?"), never for asking permission
 * to perform an action: approvals are the system's job ({@link Sensitive}). If tests show the model misusing it, fold
 * it into {@code ask_choice} with two options.
 */
@Component
public class AskUserTools implements AgentToolSet {

    public static final String ASK_CHOICE = "ask_choice";
    public static final String ASK_INPUT = "ask_input";
    public static final String ASK_YES_NO = "ask_yes_no";

    @AskUser
    @Tool(name = ASK_CHOICE, value = "Ask the user to pick from a few options when you cannot tell which one they "
            + "mean, e.g. which of their orders.")
    public String askChoice(
            @P("The question shown to the user") String question,
            @P("2 to 6 options with unique values") List<ChoiceOption> options,
            @P("True when the user may pick more than one option") boolean multiSelect) {
        throw neverExecuted();
    }

    @AskUser
    @Tool(name = ASK_INPUT, value = "Ask the user for a piece of information you need and cannot look up, "
            + "as free text.")
    public String askInput(@P("The question shown to the user") String question) {
        throw neverExecuted();
    }

    @AskUser
    @Tool(name = ASK_YES_NO, value = "Check that you understood the user correctly, e.g. \"Did you mean order "
            + "#WE45?\". Never use it to ask permission to perform an action; the system asks for that itself.")
    public String askYesNo(@P("A yes/no question") String question) {
        throw neverExecuted();
    }

    private static IllegalStateException neverExecuted() {
        return new IllegalStateException("ask tools are answered by the user and never executed");
    }
}

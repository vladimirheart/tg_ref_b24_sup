package com.example.supportbot.max;

import com.example.supportbot.service.ConversationProblemTextSupport;
import com.example.supportbot.service.QuestionnaireScope;
import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import com.example.supportbot.service.PanelRuntimeConfigClient.SelectedQuestionnaire;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import com.example.supportbot.settings.dto.QuestionRouteDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MaxConversationSession {

    record HistoryEvent(Long userId, String text, String messageType) {
    }

    record State(Long userId,
                 Long chatId,
                 String username,
                 String clientName,
                 List<QuestionFlowItemDto> flow,
                 BotSettingsDto settings,
                 QuestionnaireScope scope,
                 Map<String, String> answers,
                 List<HistoryEvent> history,
                 List<Integer> visitedQuestionIndexes,
                 OffsetDateTime startedAt,
                 Map<String, String> cachedAnswers,
                 String bootstrapProblemText,
                 boolean firstClientResponseReceived,
                 boolean reuseDecisionPending,
                 int currentIndex,
                 Selection selection) {
    }

    record Selection(ChoiceCatalog catalog,String nonce,String startEventKey,String submissionEventKey,String submittedTicketId) {
        Selection {
            MaxQuestionnaireChoiceSupport.validate(catalog,nonce);
            if(startEventKey==null || startEventKey.isBlank()) throw new IllegalStateException("Menu lost its event identity");
            if(submittedTicketId!=null && (submissionEventKey==null || submissionEventKey.isBlank()))
                throw new IllegalStateException("Submitted questionnaire lost its event identity");
        }
    }

    private final Long userId;
    private final Long chatId;
    private final String username;
    private final String clientName;
    private final List<QuestionFlowItemDto> flow;
    private final BotSettingsDto settings;
    private final QuestionnaireScope scope;
    private final Map<String, String> answers = new LinkedHashMap<>();
    private final List<HistoryEvent> history = new ArrayList<>();
    private final List<Integer> visitedQuestionIndexes = new ArrayList<>();
    private final Map<String, Integer> questionIndexes;
    private final OffsetDateTime startedAt;
    private Map<String, String> cachedAnswers = new LinkedHashMap<>();
    private String bootstrapProblemText;
    private String persistedRawPayload;
    private boolean firstClientResponseReceived = false;
    private boolean reuseDecisionPending = false;
    private int currentIndex = 0;
    private Selection selection;

    static MaxConversationSession pending(Long userId,Long chatId,String username,String clientName,ChoiceCatalog catalog,String eventKey) {
        var session=new MaxConversationSession(userId,chatId,username,clientName,List.of(),null,null);
        session.selection=new Selection(catalog,java.util.UUID.randomUUID().toString().replace("-","").substring(0,12),eventKey,null,null);
        return session;
    }

    boolean awaitingBusinessSelection() { return selection!=null && scope==null; }
    boolean selectedBusiness() { return selection!=null && scope!=null && "BUSINESS".equals(scope.scopeKind()); }
    Selection selection() { return selection; }
    MaxConversationSession choose(SelectedQuestionnaire selected,BotSettingsDto chosenSettings,List<QuestionFlowItemDto> chosenFlow) {
        if(!awaitingBusinessSelection() || selected.channelId()!=selection.catalog().channelId()
                || !selected.catalogSha256().equals(selection.catalog().catalogSha256())
                || selection.catalog().choices().stream().noneMatch(choice->choice.businessId()==selected.businessId()
                    && choice.questionTemplateId().equals(selected.questionTemplateId())
                    && choice.templateSha256().equals(selected.templateSha256())))
            throw new IllegalStateException("Questionnaire choice does not match the saved menu");
        var state=snapshot();
        var chosen=new MaxConversationSession(new State(userId,chatId,username,clientName,chosenFlow,chosenSettings,
            QuestionnaireScope.fromSelection(selected),Map.of(),state.history(),List.of(),startedAt,Map.of(),
            bootstrapProblemText,false,false,0,selection));
        chosen.restorePersistedRawPayload(persistedRawPayload);
        return chosen;
    }
    void prepareSubmission(String eventKey) {
        if(!selectedBusiness() || !isComplete() || eventKey==null || eventKey.isBlank())
            throw new IllegalStateException("Questionnaire is not ready for submission");
        if(selection.submissionEventKey()==null)
            selection=new Selection(selection.catalog(),selection.nonce(),selection.startEventKey(),eventKey,null);
    }
    void markSubmitted(String ticketId) {
        if(ticketId==null || ticketId.isBlank() || selection==null || selection.submissionEventKey()==null)
            throw new IllegalStateException("Incomplete questionnaire submission");
        selection=new Selection(selection.catalog(),selection.nonce(),selection.startEventKey(),selection.submissionEventKey(),ticketId);
    }

    MaxConversationSession(Long userId,
                           Long chatId,
                           String username,
                           String clientName,
                           List<QuestionFlowItemDto> flow,
                           BotSettingsDto settings) {
        this(userId, chatId, username, clientName, flow, settings, null);
    }

    MaxConversationSession(Long userId, Long chatId, String username, String clientName,
                           List<QuestionFlowItemDto> flow, BotSettingsDto settings, QuestionnaireScope scope) {
        this.userId = userId;
        this.chatId = chatId;
        this.username = username;
        this.clientName = clientName;
        this.flow = flow;
        this.settings = settings;
        this.scope = scope;
        this.questionIndexes = indexQuestions(flow);
        this.startedAt = OffsetDateTime.now();
        this.bootstrapProblemText = null;
        this.persistedRawPayload = null;
    }

    MaxConversationSession(State state) {
        this.userId = state.userId();
        this.chatId = state.chatId();
        this.username = state.username();
        this.clientName = state.clientName();
        this.flow = state.flow() != null ? new ArrayList<>(state.flow()) : List.of();
        this.settings = state.settings();
        this.scope = state.scope();
        this.questionIndexes = indexQuestions(this.flow);
        this.startedAt = state.startedAt() != null ? state.startedAt() : OffsetDateTime.now();
        if (state.answers() != null) {
            this.answers.putAll(state.answers());
        }
        if (state.history() != null) {
            this.history.addAll(state.history());
        }
        if (state.visitedQuestionIndexes() != null) {
            this.visitedQuestionIndexes.addAll(state.visitedQuestionIndexes());
        }
        this.cachedAnswers = state.cachedAnswers() != null ? new LinkedHashMap<>(state.cachedAnswers()) : new LinkedHashMap<>();
        this.bootstrapProblemText = state.bootstrapProblemText();
        this.persistedRawPayload = null;
        this.firstClientResponseReceived = state.firstClientResponseReceived();
        this.reuseDecisionPending = state.reuseDecisionPending();
        this.currentIndex = Math.max(0, state.currentIndex());
        this.selection = state.selection();
        if(selection!=null && ((scope==null && (!flow.isEmpty() || !answers.isEmpty()))
                || (scope!=null && flow.isEmpty())
                || (selection.submissionEventKey()!=null && (scope==null || !isComplete()))
                || (scope!=null && (!"BUSINESS".equals(scope.scopeKind())
                    || !selection.catalog().catalogSha256().equals(scope.catalogSha256())
                    || selection.catalog().choices().stream().noneMatch(choice->java.util.Objects.equals(scope.businessId(),choice.businessId())
                        && choice.questionTemplateId().equals(scope.questionTemplateId())
                        && choice.templateSha256().equals(scope.templateSha256()))))))
            throw new IllegalStateException("Saved questionnaire scope does not match its menu");
    }

    QuestionnaireScope scope() {
        return scope;
    }

    void captureBootstrapClientText(String text) {
        String normalized = ConversationProblemTextSupport.trimToNull(text);
        if (normalized == null) {
            return;
        }
        bootstrapProblemText = normalized;
        history.add(new HistoryEvent(userId, normalized, "text"));
    }

    QuestionFlowItemDto currentQuestion() {
        if (currentIndex < 0 || currentIndex >= flow.size()) {
            return null;
        }
        return flow.get(currentIndex);
    }

    void recordAnswer(String text) {
        markClientResponseReceived();
        QuestionFlowItemDto current = currentQuestion();
        if (current == null) {
            return;
        }
        String answerKey = answerKeyFor(current);
        if (answerKey != null) {
            answers.put(answerKey, mergeAnswerWithBootstrap(answerKey, text));
        }
        history.add(new HistoryEvent(userId, text, "text"));
        int answeredIndex = currentIndex;
        visitedQuestionIndexes.add(answeredIndex);
        currentIndex = resolveNextQuestionIndex(answeredIndex, current, text);
    }

    boolean isComplete() {
        return currentIndex >= flow.size();
    }

    boolean canGoBack() {
        return !visitedQuestionIndexes.isEmpty();
    }

    Map<String, String> answers() {
        return answers;
    }

    List<TicketService.TicketAttributeInput> ticketAttributes() {
        List<TicketService.TicketAttributeInput> attributes = new ArrayList<>();
        for (QuestionFlowItemDto item : flow) {
            if (item == null) {
                continue;
            }
            String answerKey = answerKeyFor(item);
            if (answerKey == null) {
                continue;
            }
            String answer = answers.get(answerKey);
            if (answer == null || answer.isBlank()) {
                continue;
            }
            String valueId = resolveValueId(item, answer);
            String valueLabel = MaxQuestionInputSupport.isChoiceQuestion(item) ? answer : null;
            attributes.add(TicketService.TicketAttributeInput.fromQuestion(item, valueId, valueLabel, answer));
        }
        return attributes;
    }

    List<HistoryEvent> history() {
        return history;
    }

    Long userId() {
        return userId;
    }

    Long chatId() {
        return chatId;
    }

    String username() {
        return username;
    }

    String clientName() {
        return clientName;
    }

    OffsetDateTime startedAt() {
        return startedAt;
    }

    BotSettingsDto settings() {
        return settings;
    }

    String persistedRawPayload() {
        return persistedRawPayload;
    }

    void restorePersistedRawPayload(String rawPayload) {
        this.persistedRawPayload = rawPayload;
    }

    void enableReusePrompt(Map<String, String> defaults) {
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        this.cachedAnswers = new LinkedHashMap<>(defaults);
        this.reuseDecisionPending = true;
    }

    boolean awaitingReuseDecision() {
        return reuseDecisionPending;
    }

    boolean consumeReuseDecision(String decision) {
        if (!reuseDecisionPending) {
            return true;
        }
        if (decision == null) {
            return false;
        }
        markClientResponseReceived();
        String normalized = decision.trim().toLowerCase();
        if (normalized.startsWith("д") || normalized.startsWith("y")) {
            applyCachedAnswers();
            reuseDecisionPending = false;
            return true;
        }
        if (normalized.startsWith("н") || normalized.startsWith("n")) {
            reuseDecisionPending = false;
            return true;
        }
        return false;
    }

    void markClientResponseReceived() {
        firstClientResponseReceived = true;
    }

    boolean shouldExpireDueToMissingFirstResponse(OffsetDateTime now, int timeoutMinutes) {
        if (firstClientResponseReceived || timeoutMinutes <= 0 || now == null) {
            return false;
        }
        return !now.isBefore(startedAt.plusMinutes(timeoutMinutes));
    }

    boolean stepBack() {
        if (visitedQuestionIndexes.isEmpty()) {
            return false;
        }
        currentIndex = visitedQuestionIndexes.remove(visitedQuestionIndexes.size() - 1);
        QuestionFlowItemDto previous = currentQuestion();
        String answerKey = answerKeyFor(previous);
        if (answerKey != null) {
            answers.remove(answerKey);
        }
        return true;
    }

    private void applyCachedAnswers() {
        answers.clear();
        visitedQuestionIndexes.clear();
        int index = 0;
        while (index < flow.size()) {
            QuestionFlowItemDto item = flow.get(index);
            String answerKey = answerKeyFor(item);
            if (answerKey == null || !cachedAnswers.containsKey(answerKey)) {
                currentIndex = index;
                return;
            }
            String answer = cachedAnswers.get(answerKey);
            answers.put(answerKey, answer);
            visitedQuestionIndexes.add(index);
            int nextIndex = resolveNextQuestionIndex(index, item, answer);
            if (nextIndex <= index) {
                currentIndex = index + 1;
                return;
            }
            index = nextIndex;
        }
        currentIndex = flow.size();
    }

    String reusePrompt() {
        return "Использовать прошлые значения? "
                + String.format("Бизнес: %s, Тип: %s, Город: %s, Локация: %s. Ответьте 'да' или 'нет'.",
                cachedAnswers.getOrDefault("business", "—"),
                cachedAnswers.getOrDefault("location_type", "—"),
                cachedAnswers.getOrDefault("city", "—"),
                cachedAnswers.getOrDefault("location_name", "—"));
    }

    String buildSummary(String ticketId) {
        StringBuilder builder = new StringBuilder();
        builder.append("Новая заявка #").append(ticketId)
                .append(" от пользователя ").append(userId).append("\n");
        builder.append("Создана: ").append(startedAt).append("\n");
        if (chatId != null) {
            builder.append("Чат: ").append(chatId).append("\n");
        }
        builder.append("\n");
        for (QuestionFlowItemDto item : flow) {
            String answerKey = answerKeyFor(item);
            builder.append(item.getText()).append(": ")
                    .append(answerKey != null ? answers.getOrDefault(answerKey, "") : "")
                    .append("\n");
        }
        return builder.toString();
    }

    State snapshot() {
        return new State(
                userId,
                chatId,
                username,
                clientName,
                new ArrayList<>(flow),
                settings,
                scope,
                new LinkedHashMap<>(answers),
                new ArrayList<>(history),
                new ArrayList<>(visitedQuestionIndexes),
                startedAt,
                new LinkedHashMap<>(cachedAnswers),
                bootstrapProblemText,
                firstClientResponseReceived,
                reuseDecisionPending,
                currentIndex,
                selection
        );
    }

    private String answerKeyFor(QuestionFlowItemDto item) {
        if (item == null) {
            return null;
        }
        String bindingKey = Optional.ofNullable(item.getBindingKey()).orElse("").trim();
        if (!bindingKey.isEmpty()) {
            return bindingKey;
        }
        if (item.getPreset() != null && item.getPreset().field() != null
                && !item.getPreset().field().isBlank()) {
            return item.getPreset().field();
        }
        return item.getId();
    }

    private String resolveValueId(QuestionFlowItemDto item, String answer) {
        if (item == null || answer == null) {
            return null;
        }
        if (item.getOptions() != null) {
            for (QuestionOptionDto option : item.getOptions()) {
                if (option == null || option.getLabel() == null) {
                    continue;
                }
                if (option.getLabel().equalsIgnoreCase(answer.trim())) {
                    return option.getId();
                }
            }
        }
        return MaxQuestionInputSupport.isPresetQuestion(item) ? answer : null;
    }

    private String mergeAnswerWithBootstrap(String answerKey, String answer) {
        if (!"problem".equals(answerKey)) {
            return answer;
        }
        return ConversationProblemTextSupport.mergeProblemText(bootstrapProblemText, answer);
    }

    private Map<String, Integer> indexQuestions(List<QuestionFlowItemDto> questions) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            QuestionFlowItemDto item = questions.get(i);
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            indexes.put(item.getId(), i);
        }
        return indexes;
    }

    private int resolveNextQuestionIndex(int sourceIndex, QuestionFlowItemDto current, String answer) {
        int sequentialIndex = sourceIndex + 1;
        if (current == null) {
            return sequentialIndex;
        }
        String routeTargetId = resolveRouteTargetId(current, answer);
        if (routeTargetId == null || routeTargetId.isBlank()) {
            return sequentialIndex;
        }
        Integer routedIndex = questionIndexes.get(routeTargetId);
        if (routedIndex == null || routedIndex <= sourceIndex) {
            return sequentialIndex;
        }
        return routedIndex;
    }

    private String resolveRouteTargetId(QuestionFlowItemDto item, String answer) {
        List<QuestionRouteDto> routes = item != null ? item.getRoutes() : null;
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        String valueId = resolveValueId(item, answer);
        if (valueId == null || valueId.isBlank()) {
            return null;
        }
        for (QuestionRouteDto route : routes) {
            if (route == null) {
                continue;
            }
            String routeValueId = Optional.ofNullable(route.getValueId()).orElse("").trim();
            if (routeValueId.equals(valueId.trim())) {
                return Optional.ofNullable(route.getNextQuestionId()).orElse("").trim();
            }
        }
        return null;
    }
}

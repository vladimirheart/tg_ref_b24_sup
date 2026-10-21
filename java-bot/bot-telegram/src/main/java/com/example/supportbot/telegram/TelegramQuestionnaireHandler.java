package com.example.supportbot.telegram;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.service.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;

/** Selected BUSINESS transport state; legacy/general-only stay with SupportBot. */
@Component
public class TelegramQuestionnaireHandler {
    static final String SESSION_PLATFORM="telegram-questionnaire-v1";
    private final RuntimeConfigService runtime;
    private final BotSettingsService settings;
    private final BotSessionStoreService store;
    private final TicketService tickets;
    private final BlacklistService blacklist;

    public TelegramQuestionnaireHandler(RuntimeConfigService runtime,BotSettingsService settings,BotSessionStoreService store,
                                        TicketService tickets,BlacklistService blacklist) {
        this.runtime=runtime;this.settings=settings;this.store=store;this.tickets=tickets;this.blacklist=blacklist;
    }

    /** true means the legacy writer/media/operator path must not process this update. */
    public boolean handle(Update update,Channel channel,Consumer<SendMessage> sender) {
        return handle(update,channel,sender,true);
    }
    public boolean handle(Update update,Channel channel,Consumer<SendMessage> sender,boolean captureBootstrap) {
        Message message=update.getMessage()!=null?update.getMessage():update.getEditedMessage();
        if(message==null || message.getChat()==null || !message.getChat().isUserChat()) return false;
        if(message.getFrom()==null || message.getFrom().getId()==null || message.getFrom().getId()<=0
                || !message.getFrom().getId().equals(message.getChatId()) || channel==null || channel.getId()==null || channel.getId()<=0
                || !channel.isActive() || !"telegram".equalsIgnoreCase(channel.getPlatform()))
            throw new IllegalStateException("Invalid Telegram questionnaire owner/channel");
        for(int attempt=0;attempt<3;attempt++) {
            try {return process(update,message,channel,sender,captureBootstrap);}
            catch(ScopedRuntimeConfigUnavailableException ex) {
                send(sender,message.getChatId(),"Вопросник недоступен или изменился. Для нового выбора отправьте /start.");return true;
            } catch(SessionStateConflictException ex) {if(attempt==2) throw ex;}
        }
        throw new IllegalStateException("Unreachable questionnaire retry");
    }

    private boolean process(Update update,Message message,Channel channel,Consumer<SendMessage> sender,boolean captureBootstrap) {
        var stored=store.load(SESSION_PLATFORM,channel.getId(),message.getFrom().getId(),State.class);
        Session session=stored.map(saved->new Session(saved.payload(),saved.rawPayload(),channel,message)).orElse(null);
        boolean shared=session!=null;
        if(!shared) {
            try {runtime.findChannelConfig(channel);}
            catch(ScopedRuntimeConfigUnavailableException ex) {shared=true;}
        }
        if(!shared) return false;
        String text=message.hasText()?message.getText().strip():"";
        var blocked=blacklist.getStatus(message.getFrom().getId());
        if(blocked.blacklisted()) {
            if("/unblock".equalsIgnoreCase(text)) return false;
            send(sender,message.getChatId(),"Ваш аккаунт заблокирован. Для запроса разблокировки отправьте /unblock.");return true;
        }
        if(message.getMessageId()==null || message.getMessageId()<=0) throw new IllegalStateException("Stable Telegram message ID is required");
        long id=message.getMessageId().longValue();
        if(update.getEditedMessage()!=null) {send(sender,message.getChatId(),"Редактирование ответов здесь пока недоступно. Для нового опроса — /start.");return true;}
        if(session!=null) {
            if(id<session.state.startMessageId || (!session.state.accepted.isEmpty() && id<Collections.max(session.state.accepted)
                    && !session.state.accepted.contains(id))) {prompt(sender,session);return true;}
            if(session.state.submissionMessageId!=null && session.state.submittedTicketId==null) {
                submit(sender,session,channel);
                if(!session.state.accepted.contains(id)) {
                    send(sender,message.getChatId(),"Обращение восстанавливается. Повторите это сообщение после регистрации обращения.");
                    throw new IllegalStateException("Retry new Telegram message after pending submission");
                }
            }
            if(id==session.state.startMessageId || session.state.accepted.contains(id)) {
                if(session.complete() && session.state.submittedTicketId==null) submit(sender,session,channel);
                else prompt(sender,session);
                return true;
            }
        }
        if("/cancel".equalsIgnoreCase(text) || "/start".equalsIgnoreCase(text)) {
            if(session!=null) {
                if(!store.deleteIfUnchanged(SESSION_PLATFORM,channel.getId(),session.state.userId,session.raw))
                    throw new SessionStateConflictException("Telegram questionnaire delete conflict");
                session=null;
            }
            if("/cancel".equalsIgnoreCase(text)) {send(sender,message.getChatId(),"Текущая заявка отменена.");return true;}
        }
        if(session==null) {
            if(TelegramQuestionnaireMenu.reserved(text)) {send(sender,message.getChatId(),"Для выбора вопросника отправьте /start.");return true;}
            var state=new State();state.channelId=channel.getId();state.userId=message.getFrom().getId();state.chatId=message.getChatId();
            state.startMessageId=id;state.startedAt=OffsetDateTime.now();state.username=message.getFrom().getUserName();
            state.clientName=(Objects.toString(message.getFrom().getFirstName(),"")+" "+Objects.toString(message.getFrom().getLastName(),"")).strip();
            state.catalog=runtime.questionnaireChoices(channel);state.nonce=UUID.randomUUID().toString().replace("-","").substring(0,12);
            if(captureBootstrap && !text.isBlank() && !text.startsWith("/")) {state.bootstrap=text;state.history.add(history(message,text,state.startedAt));}
            session=new Session(state,null,channel,message);save(session);
            if(!message.hasText() || hasMedia(message)) send(sender,message.getChatId(),"В этом опросе пока поддерживается только текст; вложения не сохранены.");
            prompt(sender,session);return true;
        }
        if(!message.hasText() || hasMedia(message)) {send(sender,message.getChatId(),"В этом опросе пока поддерживается только текст. Отправьте сообщение без вложений.");return true;}
        if(session.state.selected==null) {
            var action=TelegramQuestionnaireMenu.resolve(session.state.catalog,session.state.nonce,text);
            if(action.isPresent()) {
                if(action.get().page()) session.state.page=(int)action.get().value();
                else {
                    session.state.selected=action.get().general()?runtime.selectGeneralQuestionnaire(channel,session.state.catalog)
                        :runtime.selectQuestionnaire(channel,session.state.catalog,action.get().value());
                    session.validateSelected();session.accept(id);
                }
                save(session);
            }
            prompt(sender,session);return true;
        }
        if(TelegramQuestionnaireMenu.reserved(text)) {prompt(sender,session);return true;}
        if(text.startsWith("/")) {prompt(sender,session);return true;}
        if(session.state.submittedTicketId!=null) {followup(sender,session,channel,message,text);return true;}
        if(session.complete()) {
            submit(sender,session,channel);
            send(sender,message.getChatId(),"Повторите новое сообщение после регистрации обращения.");
            throw new IllegalStateException("Retry new Telegram message after submission");
        }
        var question=session.flow.get(session.state.index);
        if("Назад".equalsIgnoreCase(text)) {
            if(!session.state.visited.isEmpty()) {
                int previous=session.state.visited.remove(session.state.visited.size()-1);
                for(int index=previous;index<session.flow.size();index++) session.state.answers.remove(key(session.flow.get(index)));
                session.state.index=previous;session.accept(id);session.state.history.add(history(message,text,session.state.startedAt));save(session);
            }
            prompt(sender,session);return true;
        }
        List<String> options=options(question);
        String answer=options.isEmpty()?text:TelegramChoiceInputSupport.resolveDirectAnswer(text,options);
        if(options.isEmpty() && !question.isRequiredAnswer() && "Пропустить".equalsIgnoreCase(text)) answer="";
        else if((!options.isEmpty() && !options.contains(answer)) || (answer.isBlank() && question.isRequiredAnswer())) {
            prompt(sender,session);return true;
        }
        String answerKey=key(question);
        session.state.answers.put(answerKey,"problem".equals(answerKey)?ConversationProblemTextSupport.mergeProblemText(session.state.bootstrap,answer):answer);
        session.state.history.add(history(message,text,session.state.startedAt));session.accept(id);
        session.state.visited.add(session.state.index);session.state.index=session.next(question,answer);
        save(session);
        if(session.complete()) submit(sender,session,channel);else prompt(sender,session);
        return true;
    }

    private void submit(Consumer<SendMessage> sender,Session session,Channel channel) {
        var state=session.state;
        if(state.submissionMessageId==null) {
            if(tickets.findActiveTicketForUser(state.userId,identity(state),state.channelId).isPresent())
                throw new ScopedRuntimeConfigUnavailableException("A different active Telegram root already exists");
            var current="GENERAL".equals(state.selected.scopeKind())?runtime.selectGeneralQuestionnaire(channel,state.catalog)
                :runtime.selectQuestionnaire(channel,state.catalog,state.selected.businessId());
            if(!QuestionnaireScope.fromSelection(current).equals(QuestionnaireScope.fromSelection(state.selected)))
                throw new ScopedRuntimeConfigUnavailableException("Selected Telegram questionnaire changed");
            state.submissionMessageId=state.accepted.get(state.accepted.size()-1);save(session);
        }
        var scope=QuestionnaireScope.fromSelection(state.selected);
        List<TicketService.TicketAttributeInput> attributes=new ArrayList<>();
        for(var question:session.flow) {
            String answer=state.answers.get(key(question));
            if(answer!=null && !answer.isBlank()) attributes.add(TicketService.TicketAttributeInput.fromQuestion(question,valueId(question,answer),
                    options(question).isEmpty()?null:answer,answer));
        }
        var result=tickets.createConversationTicket(new ConversationTicketCreationCommand(state.userId,identity(state),state.username,state.clientName,
                new LinkedHashMap<>(state.answers),attributes,List.copyOf(state.history),channel,state.startedAt,
                eventKey(state.chatId,state.submissionMessageId),scope.businessId(),scope.scopeKind(),scope.scopeSelectionSource(),
                scope.questionTemplateId(),scope.templateSha256(),scope.catalogSha256()));
        if(result.ticketId()==null || result.ticketId().isBlank()) throw new IllegalStateException("Telegram submission lost its ticket ID");
        state.submittedTicketId=result.ticketId();save(session);
        var number=tickets.awaitClientTicketNumber(result);
        send(sender,state.chatId,number.map(n->"Спасибо! Ваше обращение №"+n+" отправлено оператору.")
                .orElse("Спасибо! Обращение принято. Номер появится после регистрации."));
    }

    private void followup(Consumer<SendMessage> sender,Session session,Channel channel,Message message,String text) {
        var state=session.state;var active=tickets.findActiveTicketForUser(state.userId,identity(state),state.channelId);
        if(active.isPresent() && !state.submittedTicketId.equals(active.get().getTicketId())) throw new IllegalStateException("Different active Telegram questionnaire root");
        var canonical=tickets.findByTicketId(state.submittedTicketId);
        if(canonical.isEmpty() || !Objects.equals(canonical.get().userId(),state.userId)) throw new IllegalStateException("Telegram questionnaire root is not registered");
        if(Set.of("closed","resolved").contains(Objects.toString(canonical.get().status(),"").toLowerCase(Locale.ROOT))) {
            send(sender,state.chatId,"Обращение закрыто. Для нового обращения отправьте /start.");return;
        }
        if(active.isEmpty()) throw new IllegalStateException("Telegram questionnaire root is not active");
        tickets.recordActiveClientMessage(new ActiveInboundClientMessageCommand(state.userId,identity(state),state.username,state.clientName,channel,
                state.submittedTicketId,text,"text",null,null,message.getMessageId().longValue(),null,null,occurredAt(message,state.startedAt)));
    }

    private void prompt(Consumer<SendMessage> sender,Session session) {
        var state=session.state;
        if(state.selected==null) {
            sender.accept(SendMessage.builder().chatId(state.chatId).text(TelegramQuestionnaireMenu.text(state.catalog,state.nonce,state.page))
                    .replyMarkup(TelegramQuestionnaireMenu.keyboard(state.catalog,state.nonce,state.page)).build());return;
        }
        if(session.complete()) {send(sender,state.chatId,"Обращение уже принято или ожидает регистрации. Для нового опроса — /start.");return;}
        var question=session.flow.get(state.index);var text=new StringBuilder(Objects.toString(question.getText(),""));
        var options=options(question);for(int index=0;index<options.size();index++) text.append('\n').append(index+1).append(". ").append(options.get(index));
        if(options.isEmpty() && !question.isRequiredAnswer()) text.append("\nМожно пропустить: Пропустить.");
        if(!state.visited.isEmpty()) text.append("\nНазад — к предыдущему вопросу.");
        send(sender,state.chatId,text.toString());
    }
    private void save(Session session) {
        session.raw=store.saveIfUnchanged(SESSION_PLATFORM,session.state.channelId,session.state.userId,session.raw,session.state)
                .orElseThrow(()->new SessionStateConflictException("Telegram questionnaire changed concurrently"));
    }
    private static void send(Consumer<SendMessage> sender,long chatId,String text) {
        sender.accept(SendMessage.builder().chatId(chatId).text(text).replyMarkup(new ReplyKeyboardRemove(true)).build());
    }
    static String eventKey(long chatId,long messageId) {return "telegram-message:"+chatId+":"+messageId;}
    private static boolean hasMedia(Message message) {
        return message.hasDocument() || message.hasPhoto() || message.hasVideo() || message.hasAudio() || message.hasVoice()
                || message.hasAnimation() || message.getSticker()!=null || message.getVideoNote()!=null;
    }
    private static String identity(State state) {return state.username!=null && !state.username.isBlank()?state.username:Long.toString(state.userId);}
    private static String key(QuestionFlowItemDto question) {
        return question.getBindingKey()!=null && !question.getBindingKey().isBlank()?question.getBindingKey():question.getId();
    }
    private static List<String> options(QuestionFlowItemDto question) {
        if(!"select".equalsIgnoreCase(question.getType()) || question.getOptions()==null) return List.of();
        return question.getOptions().stream().filter(Objects::nonNull).map(QuestionOptionDto::getLabel).filter(Objects::nonNull).toList();
    }
    private static String valueId(QuestionFlowItemDto question,String answer) {
        return question.getOptions()==null?null:question.getOptions().stream().filter(Objects::nonNull)
                .filter(option->Objects.equals(option.getLabel(),answer)).map(QuestionOptionDto::getId).findFirst().orElse(null);
    }
    private static OffsetDateTime occurredAt(Message message,OffsetDateTime fallback) {
        return message.getDate()!=null?OffsetDateTime.ofInstant(Instant.ofEpochSecond(message.getDate()),ZoneOffset.UTC):fallback;
    }
    private static ConversationHistoryEntry history(Message message,String text,OffsetDateTime fallback) {
        return new ConversationHistoryEntry(message.getFrom().getId(),text,"text",null,null,message.getMessageId().toString(),occurredAt(message,fallback));
    }

    public static final class State {
        public int version=1,index,page;
        public long channelId,userId,chatId,startMessageId;
        public String username,clientName,nonce,bootstrap,submittedTicketId;
        public ChoiceCatalog catalog;
        public SelectedQuestionnaire selected;
        public OffsetDateTime startedAt;
        public Long submissionMessageId;
        public Map<String,String> answers=new LinkedHashMap<>();
        public List<ConversationHistoryEntry> history=new ArrayList<>();
        public List<Integer> visited=new ArrayList<>();
        public List<Long> accepted=new ArrayList<>();
    }
    private final class Session {
        final State state;String raw;List<QuestionFlowItemDto> flow=List.of();
        Session(State state,String raw,Channel channel,Message message) {
            this.state=state;this.raw=raw;
            if(state==null || state.version!=1 || state.channelId!=channel.getId() || state.userId!=message.getFrom().getId()
                    || state.chatId!=message.getChatId() || state.userId!=state.chatId || state.startMessageId<=0 || state.startedAt==null
                    || state.catalog==null || state.catalog.channelId()!=state.channelId || state.answers==null || state.visited==null
                    || state.history==null || state.accepted==null || state.accepted.size()>4096 || state.accepted.stream().anyMatch(id->id==null || id<=0)
                    || new HashSet<>(state.accepted).size()!=state.accepted.size()
                    || state.history.stream().anyMatch(entry->entry==null || !Objects.equals(entry.userId(),state.userId)
                        || !"text".equals(entry.messageType()) || entry.attachmentPath()!=null || entry.attachmentName()!=null
                        || entry.providerMessageId()==null || !entry.providerMessageId().matches("[1-9][0-9]*") || entry.occurredAt()==null))
                throw new IllegalStateException("Invalid saved Telegram questionnaire identity");
            TelegramQuestionnaireMenu.validate(state.catalog,state.nonce);
            if(state.page<0 || state.page>=TelegramQuestionnaireMenu.pages(state.catalog)) throw new IllegalStateException("Invalid Telegram menu page");
            if(state.selected!=null) validateSelected();
            else if(!state.answers.isEmpty() || !state.visited.isEmpty() || state.index!=0 || state.submissionMessageId!=null || state.submittedTicketId!=null)
                throw new IllegalStateException("Pending Telegram menu contains selected answers");
            if(state.index<0 || state.index>flow.size() || state.visited.stream().anyMatch(index->index==null || index<0 || index>=flow.size())
                    || (state.submissionMessageId!=null && (!complete() || !state.accepted.contains(state.submissionMessageId)))
                    || (state.submittedTicketId!=null && (state.submissionMessageId==null || state.submittedTicketId.isBlank())))
                throw new IllegalStateException("Invalid Telegram questionnaire progress");
        }
        void validateSelected() {
            var selected=state.selected;QuestionnaireScope.fromSelection(selected);
            if(selected.version()!=state.catalog.version() || selected.channelId()!=state.channelId || !state.catalog.catalogSha256().equals(selected.catalogSha256())
                    || state.catalog.choices().stream().noneMatch(choice->choice.scopeKind().equals(selected.scopeKind()) && Objects.equals(choice.businessId(),selected.businessId())
                        && choice.questionTemplateId().equals(selected.questionTemplateId()) && choice.templateSha256().equals(selected.templateSha256())))
                throw new IllegalStateException("Telegram questionnaire differs from its saved menu");
            flow=GeneralQuestionnaireFlow.from(settings.questionFlow(settings.loadSelectedQuestionnaire(selected)));
            var keys=new HashSet<String>();
            for(var question:flow) {
                if(question.getPreset()!=null || "preset".equalsIgnoreCase(question.getType()))
                    throw new IllegalStateException("Scoped Telegram presets are not available");
                keys.add(key(question));
            }
            if(!keys.containsAll(state.answers.keySet())) throw new IllegalStateException("Unexpected saved Telegram answers");
        }
        boolean complete() {return state.selected!=null && state.index>=flow.size();}
        void accept(long id) {
            if(!state.accepted.contains(id)) state.accepted.add(id);
            if(state.accepted.size()>4096) throw new IllegalStateException("Telegram questionnaire event budget exceeded");
        }
        int next(QuestionFlowItemDto question,String answer) {
            String value=valueId(question,answer);
            if(value!=null && question.getRoutes()!=null) for(var route:question.getRoutes()) {
                if(route!=null && value.equals(route.getValueId())) for(int index=state.index+1;index<flow.size();index++)
                    if(Objects.equals(flow.get(index).getId(),route.getNextQuestionId())) return index;
            }
            return state.index+1;
        }
    }
}

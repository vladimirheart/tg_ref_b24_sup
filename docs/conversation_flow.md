# Conversation Handler Finite-State Flow

This document summarises the dialogue scenarios implemented in `bot_support.py` using
`ConversationHandler`. Each state corresponds to a stage in the helpdesk wizard and
includes the transitions that advance or terminate the conversation.

## Legend
- **State name** – Constant defined in `bot_support.py`.
- **Entry** – Callback or condition that leads into the state.
- **Prompts / actions** – Messages sent to the user when entering the state.
- **Transitions** – Inputs that move the conversation to the next state or finish it.

## Start / Previous Selection Confirmation
- **State**: implicit `entry_points` for `/start` (`start`).
- **Prompts**:
  - Checks blacklist via `_guard_blacklisted_user` (may jump directly to `UNBLOCK_REASON`).
  - Loads latest closed ticket for the current channel (`context.user_data['last_selection']`).
  - If found, sends summary and keyboard with `"✅ Да"` / `"↩️ Нет"` prompting reuse (`ReplyKeyboardMarkup`).
- **Transitions**:
  - To `PREV_STEP` when there is a cached selection (`start`).
  - Directly to `BUSINESS` when no cached selection and no active tickets (`start`).
  - Ends (`ConversationHandler.END`) when an active `pending` ticket exists or blacklist guard ends the flow.

## `PREV_STEP` – Reuse Confirmation (`previous_choice_decision`)
- **Prompts**: Reiterates the request to confirm or restart.
- **Transitions**:
  - `"✅ Да"` → Restores cached `business`, `location_type`, `city`, `location_name`, prompts for problem text, moves to `PROBLEM`.
  - Any other value (including `"↩️ Нет"`) → Presents business selection keyboard, moves to `BUSINESS`.
  - Blacklist trigger via `_guard_blacklisted_user` can divert to `UNBLOCK_REASON`.

## Location Wizard
1. **`BUSINESS` (`business_choice`)**
   - **Prompts**: Keyboard of businesses from `BUSINESS_OPTIONS`.
   - **Transitions**:
     - Valid option → stores `context.user_data['business']`, prompts location type, moves to `LOCATION_TYPE`.
     - `"🚫 Отмена"` → Ends conversation and clears `user_data`.
     - Invalid input → re-prompts same state.

2. **`LOCATION_TYPE` (`location_type_choice`)**
   - **Prompts**: Keyboard derived from `LOCATIONS[business]`.
   - **Transitions**:
     - `"◀️ Назад"` → Returns to `BUSINESS`.
     - Valid option → stores `location_type`, prompts city, moves to `CITY`.
     - `"🚫 Отмена"` → Ends.
     - Invalid input → re-prompts.

3. **`CITY` (`city_choice`)**
   - **Prompts**: Keyboard with cities for selected business & location type.
   - **Transitions**:
     - `"◀️ Назад"` → Returns to `LOCATION_TYPE`.
     - `"🚫 Отмена"` → Ends.
     - Valid option → stores `city`, prompts location name, moves to `LOCATION_NAME`.
     - Invalid input → re-prompts.

4. **`LOCATION_NAME` (`location_name_choice`)**
   - **Prompts**: Keyboard with concrete locations under selected city.
   - **Transitions**:
     - `"◀️ Назад"` → Returns to `CITY`.
     - `"🚫 Отмена"` → Ends.
     - Valid option → stores `location_name`, prompts free-form problem entry, moves to `PROBLEM`.
     - Invalid input → re-prompts.

## Problem Description & Media Collection (`PROBLEM`)
- **Handler**: `problem_description` for text plus `save_user_media` / `save_user_contact`.
- **Prompts**: Requests detailed problem description and allows media uploads.
- **Transitions**:
  - `"◀️ Назад"` → Returns to `LOCATION_NAME`.
  - `"🚫 Отмена"` → Ends and clears state.
  - First valid text → Creates ticket, persists message, forwards to support chat, stays in `ConversationHandler.END` after submission.
  - Media / contact messages are captured by additional handlers while staying in `PROBLEM` until final text submission.

## Rating Flow (out-of-band handler)
- Outside the `ConversationHandler`, `handle_feedback` intercepts numeric replies (`filters.Regex('^\d+$')`).
- It validates presence of a pending feedback request in `pending_feedback_requests` for the channel.
- **Transitions**:
  - Valid rating within configured scale → stores feedback, optionally sends follow-up questions or thank-you message, ends processing (`ApplicationHandlerStop`).
  - Invalid rating → Prompts with allowed range, stays awaiting valid input.

## Unblock Request Flow (`UNBLOCK_REASON`)
- Entered when `_guard_blacklisted_user` detects `client_blacklist.is_blacklisted` without pending request.
- **Handler**: `handle_unblock_reason` (not part of main wizard order but registered in `states`).
- **Prompts**:
  - Asks the user to describe why the account should be unblocked.
- **Transitions**:
  - Receives reason → Stores request via `_store_unblock_request`, acknowledges, ends.
  - `/cancel` fallback → Cancels and ends.

## Conversation Termination
- `cancel` command or `"🚫 Отмена"` button → replies "❌ Отменено." with `ReplyKeyboardRemove`, ends conversation.
- Any guard condition (active ticket, blacklist with existing request) leads to `ConversationHandler.END` without entering the wizard.

## Summary of Automaton
```
/start
  ├─ blacklist? → UNBLOCK_REASON → END
  ├─ active pending ticket? → END
  ├─ cached selection? → PREV_STEP
  │    ├─ confirm → PROBLEM
  │    └─ restart → BUSINESS
  └─ otherwise → BUSINESS
BUSINESS → LOCATION_TYPE → CITY → LOCATION_NAME → PROBLEM → END
                           ↑        ↑             ↑
                        back     back          back
```
The rating flow runs in parallel to the wizard, while the unblock request path is an alternative start branch triggered by blacklist checks.

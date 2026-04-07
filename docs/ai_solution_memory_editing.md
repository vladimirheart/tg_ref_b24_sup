# AI Solution Memory Editing

This document explains how to edit the agent's stored answers.

## Where to edit

1. Open `Dialogs -> AI Ops`.
2. Use the `AI Solution Memory` block.
3. Search by question/answer text (optional).
4. Edit:
   - `query_text` (the user question pattern),
   - `solution_text` (the answer used by AI retrieval).
5. Click `Save`.

## API (if needed)

- List:
  - `GET /api/dialogs/ai-solution-memory?limit=100&query=keyword`
- Update:
  - `POST /api/dialogs/ai-solution-memory/{queryKey}`
  - body:
    ```json
    {
      "query_text": "new question text",
      "solution_text": "new answer text",
      "review_required": false
    }
    ```

## Notes

- `queryKey` is stable for an existing memory row.
- If `review_required=true`, the entry remains marked for review workflows.
- After update, the agent can use the new answer for matching requests.

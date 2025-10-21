"""Helpers for bot-related configuration shared between the panel and bot runtime."""
from __future__ import annotations

import uuid
from typing import Any, Dict, Iterable, Mapping, Sequence

DEFAULT_BOT_PRESET_DEFINITIONS: Dict[str, Dict[str, Any]] = {
    "locations": {
        "label": "Структура локаций",
        "fields": {
            "business": {"label": "Бизнес"},
            "location_type": {"label": "Тип бизнеса"},
            "city": {"label": "Город"},
            "location_name": {"label": "Локация"},
        },
    }
}


def _ensure_uuid(value: Any) -> str:
    if isinstance(value, str) and value.strip():
        return value.strip()
    return uuid.uuid4().hex


def _prepare_preset_lookup(definitions: Mapping[str, Any]) -> Dict[tuple[str, str], Dict[str, Any]]:
    lookup: Dict[tuple[str, str], Dict[str, Any]] = {}
    for group, group_data in (definitions or {}).items():
        fields = group_data.get("fields") if isinstance(group_data, dict) else None
        if not isinstance(fields, Mapping):
            continue
        for field_key, meta in fields.items():
            label = ""
            options: Sequence[str] | None = None
            if isinstance(meta, Mapping):
                label = str(meta.get("label") or "").strip()
                raw_options = meta.get("options") if isinstance(meta.get("options"), Iterable) else None
                if isinstance(raw_options, Iterable) and not isinstance(raw_options, (str, bytes)):
                    prepared: list[str] = []
                    for option in raw_options:
                        if isinstance(option, str) and option.strip():
                            prepared.append(option.strip())
                    if prepared:
                        options = prepared
            lookup[(str(group), str(field_key))] = {
                "label": label or str(field_key).replace("_", " ").title(),
                "options": list(options) if options else [],
            }
    return lookup


def _default_rating_prompt(scale: int) -> str:
    if scale <= 1:
        return "Пожалуйста, оцените качество ответа: отправьте число 1."
    return f"Пожалуйста, оцените качество ответа от 1 до {scale}."


def _build_default_responses(scale: int) -> list[Dict[str, Any]]:
    responses: list[Dict[str, Any]] = []
    upper_bound = max(scale, 1)
    for value in range(1, upper_bound + 1):
        responses.append(
            {
                "value": value,
                "text": f"Спасибо за вашу оценку {value}! Нам важно ваше мнение.",
            }
        )
    return responses


def default_bot_settings(definitions: Mapping[str, Any] | None = None) -> Dict[str, Any]:
    defs = definitions if isinstance(definitions, Mapping) else DEFAULT_BOT_PRESET_DEFINITIONS
    lookup = _prepare_preset_lookup(defs)
    default_template_id = "template-default"
    default_questions: list[Dict[str, Any]] = []
    order = 1
    for group, field, fallback in (
        ("locations", "business", "Выберите бизнес"),
        ("locations", "location_type", "Выберите тип бизнеса"),
        ("locations", "city", "Выберите город"),
        ("locations", "location_name", "Укажите локацию"),
    ):
        meta = lookup.get((group, field)) or {}
        default_questions.append(
            {
                "id": field,
                "type": "preset",
                "text": meta.get("label") or fallback,
                "preset": {"group": group, "field": field},
                "order": order,
            }
        )
        order += 1

    default_scale = 5
    rating_prompt = _default_rating_prompt(default_scale)

    settings = {
        "question_templates": [
            {
                "id": default_template_id,
                "name": "Основной шаблон вопросов",
                "question_flow": default_questions,
            }
        ],
        "active_template_id": default_template_id,
        "rating_system": {
            "prompt_text": rating_prompt,
            "scale_size": default_scale,
            "responses": _build_default_responses(default_scale),
        },
    }
    settings["question_flow"] = default_questions
    return settings


def _sanitize_question_flow(
    raw_flow: Any,
    *,
    allowed_presets: set[tuple[str, str]],
    lookup: Mapping[tuple[str, str], Dict[str, Any]],
) -> list[Dict[str, Any]]:
    sanitized: list[Dict[str, Any]] = []
    if isinstance(raw_flow, Iterable) and not isinstance(raw_flow, (str, bytes)):
        for order, item in enumerate(raw_flow, start=1):
            if isinstance(item, Mapping):
                q_type = str(item.get("type") or item.get("kind") or "custom").strip().lower()
                if q_type not in {"custom", "preset"}:
                    q_type = "custom"
                text = str(item.get("text") or item.get("label") or "").strip()
                question_id = _ensure_uuid(item.get("id"))
                entry: Dict[str, Any] = {
                    "id": question_id,
                    "type": q_type,
                    "order": order,
                }
                if q_type == "preset":
                    preset_payload = item.get("preset") if isinstance(item.get("preset"), Mapping) else {}
                    group = str(preset_payload.get("group") or item.get("group") or "").strip()
                    field = str(preset_payload.get("field") or item.get("field") or "").strip()
                    key = (group, field)
                    if key not in allowed_presets:
                        continue
                    entry["preset"] = {"group": group, "field": field}
                    if not text:
                        meta = lookup.get(key) or {}
                        text = meta.get("label") or field
                    raw_excluded = item.get("excluded_options") or item.get("exclude")
                    excluded: list[str] = []
                    if isinstance(raw_excluded, Mapping):
                        candidates = raw_excluded.values()
                    elif isinstance(raw_excluded, Iterable) and not isinstance(raw_excluded, (str, bytes)):
                        candidates = raw_excluded
                    else:
                        candidates = []
                    available = lookup.get(key, {}).get("options") or []
                    allowed_values = {str(option) for option in available}
                    for candidate in candidates:
                        if isinstance(candidate, str) and candidate.strip():
                            value = candidate.strip()
                            if allowed_values:
                                if value in allowed_values and value not in excluded:
                                    excluded.append(value)
                            elif value not in excluded:
                                excluded.append(value)
                    if excluded:
                        entry["excluded_options"] = excluded
                else:
                    if not text:
                        continue
                entry["text"] = text
                sanitized.append(entry)
            elif isinstance(item, str) and item.strip():
                sanitized.append(
                    {
                        "id": _ensure_uuid(None),
                        "type": "custom",
                        "order": order,
                        "text": item.strip(),
                    }
                )
    return sanitized


def _sanitize_rating_responses(
    raw_responses: Any,
    *,
    scale: int,
    defaults: Sequence[Mapping[str, Any]],
) -> list[Dict[str, Any]]:
    collected: Dict[int, str] = {}
    if isinstance(raw_responses, Mapping):
        iterable = raw_responses.items()
    elif isinstance(raw_responses, Iterable) and not isinstance(raw_responses, (str, bytes)):
        iterable = raw_responses
    else:
        iterable = []

    for item in iterable:
        if isinstance(item, Mapping):
            value_raw = item.get("value")
            text_raw = item.get("text") or item.get("label")
        elif isinstance(item, (list, tuple)) and len(item) >= 2:
            value_raw, text_raw = item[0], item[1]
        else:
            continue
        try:
            value = int(value_raw)
        except (TypeError, ValueError):
            continue
        if value < 1 or value > scale:
            continue
        text = str(text_raw or "").strip()
        if text:
            collected[value] = text

    defaults_map = {
        int(entry.get("value")): str(entry.get("text") or "").strip()
        for entry in defaults
        if isinstance(entry, Mapping)
    }

    responses: list[Dict[str, Any]] = []
    for value in range(1, max(scale, 1) + 1):
        text = collected.get(value) or defaults_map.get(value) or f"Спасибо за вашу оценку {value}!"
        responses.append({"value": value, "text": text})
    return responses


def sanitize_bot_settings(
    raw: Any,
    *,
    definitions: Mapping[str, Any] | None = None,
    max_scale: int = 10,
) -> Dict[str, Any]:
    defs = definitions if isinstance(definitions, Mapping) else DEFAULT_BOT_PRESET_DEFINITIONS
    defaults = default_bot_settings(defs)
    if not isinstance(raw, Mapping):
        return defaults

    lookup = _prepare_preset_lookup(defs)
    allowed_presets = set(lookup)

    templates: list[Dict[str, Any]] = []
    seen_ids: set[str] = set()
    raw_templates = raw.get("question_templates")
    if isinstance(raw_templates, Iterable) and not isinstance(raw_templates, (str, bytes)):
        for item in raw_templates:
            if not isinstance(item, Mapping):
                continue
            template_id = _ensure_uuid(item.get("id"))
            if template_id in seen_ids:
                template_id = _ensure_uuid(None)
            seen_ids.add(template_id)
            name = str(item.get("name") or "").strip() or "Шаблон вопросов"
            description = str(item.get("description") or "").strip()
            flow_source = item.get("question_flow")
            if not isinstance(flow_source, Iterable) or isinstance(flow_source, (str, bytes)):
                flow_source = item.get("questions")
            sanitized_flow = _sanitize_question_flow(
                flow_source,
                allowed_presets=allowed_presets,
                lookup=lookup,
            )
            if not sanitized_flow:
                continue
            template_entry: Dict[str, Any] = {
                "id": template_id,
                "name": name,
                "question_flow": sanitized_flow,
            }
            if description:
                template_entry["description"] = description
            templates.append(template_entry)

    fallback_flow = _sanitize_question_flow(
        raw.get("question_flow"), allowed_presets=allowed_presets, lookup=lookup
    )
    if fallback_flow and not templates:
        templates.append(
            {
                "id": defaults["question_templates"][0]["id"],
                "name": "Импортированный сценарий",
                "question_flow": fallback_flow,
            }
        )

    if not templates:
        templates = defaults["question_templates"]

    raw_active = raw.get("active_template_id")
    if isinstance(raw_active, str) and raw_active.strip():
        active_template_id = raw_active.strip()
    else:
        active_template_id = templates[0]["id"]
    if active_template_id not in {tpl["id"] for tpl in templates}:
        active_template_id = templates[0]["id"]

    rating_defaults = defaults["rating_system"]
    rating_raw = raw.get("rating_system") if isinstance(raw.get("rating_system"), Mapping) else {}
    raw_scale = rating_raw.get("scale_size")
    try:
        scale = int(raw_scale)
    except (TypeError, ValueError):
        scale = rating_defaults.get("scale_size", 5)
    if scale < 1:
        scale = rating_defaults.get("scale_size", 5)
    if max_scale and scale > max_scale:
        scale = max_scale

    prompt = str(rating_raw.get("prompt_text") or rating_raw.get("prompt") or "").strip()
    if not prompt:
        prompt = str(rating_defaults.get("prompt_text") or _default_rating_prompt(scale))

    responses = _sanitize_rating_responses(
        rating_raw.get("responses"),
        scale=scale,
        defaults=rating_defaults.get("responses", []),
    )

    rating = {
        "prompt_text": prompt,
        "scale_size": scale,
        "responses": responses,
    }

    active_template = next((tpl for tpl in templates if tpl["id"] == active_template_id), templates[0])

    return {
        "question_templates": templates,
        "active_template_id": active_template_id,
        "question_flow": active_template.get("question_flow", []),
        "rating_system": rating,
    }


def build_location_presets(
    location_tree: Mapping[str, Any] | None,
    *,
    base_definitions: Mapping[str, Any] | None = None,
) -> Dict[str, Any]:
    defs = base_definitions if isinstance(base_definitions, Mapping) else DEFAULT_BOT_PRESET_DEFINITIONS
    result: Dict[str, Any] = {}

    businesses: set[str] = set()
    location_types: set[str] = set()
    cities: set[str] = set()
    location_names: set[str] = set()

    if isinstance(location_tree, Mapping):
        for business, type_dict in location_tree.items():
            if isinstance(business, str) and business.strip():
                businesses.add(business.strip())
            if not isinstance(type_dict, Mapping):
                continue
            for loc_type, city_dict in type_dict.items():
                if isinstance(loc_type, str) and loc_type.strip():
                    location_types.add(loc_type.strip())
                if not isinstance(city_dict, Mapping):
                    continue
                for city, locations in city_dict.items():
                    if isinstance(city, str) and city.strip():
                        cities.add(city.strip())
                    if isinstance(locations, Iterable):
                        for name in locations:
                            if isinstance(name, str) and name.strip():
                                location_names.add(name.strip())

    option_map = {
        "business": sorted(businesses),
        "location_type": sorted(location_types),
        "city": sorted(cities),
        "location_name": sorted(location_names),
    }

    for group_key, group_data in defs.items():
        group_label = ""
        if isinstance(group_data, Mapping):
            group_label = str(group_data.get("label") or "").strip()
            fields = group_data.get("fields") if isinstance(group_data.get("fields"), Mapping) else {}
        else:
            fields = {}
        prepared_fields: Dict[str, Any] = {}
        for field_key, meta in fields.items():
            field_label = ""
            if isinstance(meta, Mapping):
                field_label = str(meta.get("label") or "").strip()
            options = option_map.get(field_key, [])
            prepared_fields[field_key] = {
                "label": field_label or field_key,
                "options": options,
            }
        result[group_key] = {
            "label": group_label or group_key,
            "fields": prepared_fields,
        }
    return result


def rating_scale(bot_settings: Mapping[str, Any] | None, *, default: int = 5) -> int:
    if not isinstance(bot_settings, Mapping):
        return default
    rating = bot_settings.get("rating_system")
    if isinstance(rating, Mapping):
        try:
            scale = int(rating.get("scale_size"))
            if scale >= 1:
                return scale
        except (TypeError, ValueError):
            pass
    return default


def rating_allowed_values(bot_settings: Mapping[str, Any] | None) -> set[str]:
    scale = rating_scale(bot_settings)
    return {str(i) for i in range(1, scale + 1)}


def rating_prompt(bot_settings: Mapping[str, Any] | None, *, default: str | None = None) -> str:
    if not isinstance(bot_settings, Mapping):
        return default or _default_rating_prompt(rating_scale(bot_settings))
    rating = bot_settings.get("rating_system")
    if not isinstance(rating, Mapping):
        return default or _default_rating_prompt(rating_scale(bot_settings))
    prompt = rating.get("prompt_text")
    if isinstance(prompt, str) and prompt.strip():
        return prompt.strip()
    return default or _default_rating_prompt(rating_scale(bot_settings))


def rating_responses(bot_settings: Mapping[str, Any] | None) -> Dict[str, str]:
    if not isinstance(bot_settings, Mapping):
        return {}
    rating = bot_settings.get("rating_system")
    if not isinstance(rating, Mapping):
        return {}
    responses_raw = rating.get("responses")
    result: Dict[str, str] = {}
    if isinstance(responses_raw, Iterable) and not isinstance(responses_raw, (str, bytes)):
        for item in responses_raw:
            if not isinstance(item, Mapping):
                continue
            try:
                value = int(item.get("value"))
            except (TypeError, ValueError):
                continue
            text = str(item.get("text") or "").strip()
            if text:
                result[str(value)] = text
    return result


def rating_response_for(bot_settings: Mapping[str, Any] | None, value: str | int) -> str | None:
    responses = rating_responses(bot_settings)
    key = str(value)
    if key in responses:
        return responses[key]
    return None

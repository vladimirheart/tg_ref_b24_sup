"""Helpers for bot-related configuration shared between the panel and bot runtime."""
from __future__ import annotations

import uuid
from typing import Any, Dict, Iterable, Mapping

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
            if isinstance(meta, Mapping):
                label = str(meta.get("label") or "").strip()
            lookup[(str(group), str(field_key))] = {
                "label": label or str(field_key).replace("_", " ").title(),
            }
    return lookup


def default_bot_settings(definitions: Mapping[str, Any] | None = None) -> Dict[str, Any]:
    defs = definitions if isinstance(definitions, Mapping) else DEFAULT_BOT_PRESET_DEFINITIONS
    lookup = _prepare_preset_lookup(defs)
    def _label(group: str, field: str, fallback: str) -> str:
        meta = lookup.get((group, field)) or {}
        return meta.get("label") or fallback

    return {
        "question_flow": [
            {
                "id": "business",
                "type": "preset",
                "text": _label("locations", "business", "Выберите бизнес"),
                "preset": {"group": "locations", "field": "business"},
                "order": 1,
            },
            {
                "id": "location_type",
                "type": "preset",
                "text": _label("locations", "location_type", "Выберите тип бизнеса"),
                "preset": {"group": "locations", "field": "location_type"},
                "order": 2,
            },
            {
                "id": "city",
                "type": "preset",
                "text": _label("locations", "city", "Выберите город"),
                "preset": {"group": "locations", "field": "city"},
                "order": 3,
            },
            {
                "id": "location_name",
                "type": "preset",
                "text": _label("locations", "location_name", "Укажите локацию"),
                "preset": {"group": "locations", "field": "location_name"},
                "order": 4,
            },
        ],
        "rating_system": {
            "scale_size": 5,
            "post_actions": [],
        },
    }


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
    allowed_presets = {key for key in lookup}

    sanitized_flow = []
    raw_flow = raw.get("question_flow")
    if isinstance(raw_flow, Iterable):
        for order, item in enumerate(raw_flow, start=1):
            if not isinstance(item, Mapping):
                continue
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
                    # Skip invalid presets entirely
                    continue
                entry["preset"] = {"group": group, "field": field}
                if not text:
                    meta = lookup.get(key) or {}
                    text = meta.get("label") or field
            else:
                if not text:
                    # Custom question without text is useless — skip
                    continue
            entry["text"] = text
            sanitized_flow.append(entry)

    if not sanitized_flow:
        sanitized_flow = defaults["question_flow"]

    rating_defaults = defaults["rating_system"]
    rating_raw = raw.get("rating_system")
    rating: Dict[str, Any] = {
        "scale_size": rating_defaults.get("scale_size", 5),
        "post_actions": list(rating_defaults.get("post_actions", [])),
    }
    if isinstance(rating_raw, Mapping):
        raw_scale = rating_raw.get("scale_size")
        try:
            scale = int(raw_scale)
        except (TypeError, ValueError):
            scale = rating_defaults.get("scale_size", 5)
        if scale < 1:
            scale = rating_defaults.get("scale_size", 5)
        if max_scale and scale > max_scale:
            scale = max_scale
        rating["scale_size"] = scale
        actions = rating_raw.get("post_actions")
        if isinstance(actions, Iterable) and not isinstance(actions, (str, bytes)):
            cleaned = []
            for action in actions:
                if isinstance(action, Mapping):
                    label = str(action.get("text") or action.get("label") or "").strip()
                else:
                    label = str(action or "").strip()
                if label:
                    cleaned.append(label)
            rating["post_actions"] = cleaned
        elif isinstance(actions, str):
            lines = [line.strip() for line in actions.splitlines() if line.strip()]
            if lines:
                rating["post_actions"] = lines
    return {
        "question_flow": sanitized_flow,
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


def rating_actions(bot_settings: Mapping[str, Any] | None) -> list[str]:
    if not isinstance(bot_settings, Mapping):
        return []
    rating = bot_settings.get("rating_system")
    if not isinstance(rating, Mapping):
        return []
    actions = rating.get("post_actions")
    if isinstance(actions, Iterable) and not isinstance(actions, (str, bytes)):
        cleaned = []
        for action in actions:
            label = str(action or "").strip()
            if label:
                cleaned.append(label)
        return cleaned
    if isinstance(actions, str):
        return [line.strip() for line in actions.splitlines() if line.strip()]
    return []

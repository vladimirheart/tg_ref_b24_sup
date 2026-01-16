package com.example.supportbot.settings;

import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.RatingTemplateDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotSettingsServiceTest {

    private ObjectMapper objectMapper;
    private BotSettingsService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new BotSettingsService(objectMapper);
    }

    @Test
    void defaultSettingsShouldExposePresetQuestionsAndRatings() {
        BotSettingsDto defaults = service.buildDefaultSettings();

        assertThat(defaults.getQuestionTemplates()).hasSize(1);
        assertThat(defaults.getQuestionFlow()).hasSize(4);
        assertThat(defaults.getRatingTemplates()).hasSize(1);
        assertThat(defaults.getRatingSystem().getScaleSize()).isEqualTo(5);
        assertThat(defaults.getRatingSystem().getResponses())
                .extracting("text")
                .allMatch(text -> text.contains("Спасибо"));
    }

    @Test
    void sanitizeShouldNormalizeSettings() throws IOException {
        Map<String, Object> raw = objectMapper.readValue(
                """
                        {
                          "question_templates": [
                            {
                              "id": "q-1",
                              "name": " Template 1 ",
                              "question_flow": [
                                {"id": "", "type": "preset", "preset": {"group": "locations", "field": "business"}},
                                {"type": "preset", "group": "locations", "field": "unknown"},
                                {"type": "custom", "text": "  Hello "},
                                "   Another question   "
                              ]
                            },
                            {
                              "id": "q-1",
                              "name": "",
                              "questions": [
                                {"type": "preset", "group": "locations", "field": "city", "excluded_options": ["City A", "City B", "Invalid"]},
                                {"type": "custom", "text": ""}
                              ]
                            }
                          ],
                          "active_template_id": "q-1",
                          "question_flow": [
                            {"type": "preset", "group": "locations", "field": "city"},
                            "Fallback?"
                          ],
                          "rating_templates": [
                            {
                              "id": "rate1",
                              "name": " ",
                              "scale_size": "7",
                              "prompt": "  ",
                              "responses": [
                                {"value": 1, "text": "bad"},
                                {"value": 9, "text": "bad"}
                              ]
                            },
                            {
                              "id": "rate1",
                              "name": "",
                              "scale": "0",
                              "prompt_text": "",
                              "responses": [
                                {"value": "2", "label": "ok"},
                                {"value": 3, "text": ""}
                              ]
                            }
                          ],
                          "active_rating_template_id": "rate1",
                          "rating_system": {"scaleSize": "4", "responses": {"1": "one", "2": "two", "invalid": "bad"}}
                        }
                        """,
                new TypeReference<>() {
                });

        BotSettingsDto sanitized = service.sanitizeFromJson(raw);

        assertThat(sanitized.getQuestionTemplates()).hasSize(2);
        QuestionFlowItemDto firstQuestion = sanitized.getQuestionTemplates().get(0).getQuestionFlow().get(0);
        assertThat(firstQuestion.getType()).isEqualTo("preset");
        assertThat(firstQuestion.getPreset()).isNotNull();
        assertThat(firstQuestion.getText()).isEqualTo("Бизнес");

        List<QuestionFlowItemDto> flow = sanitized.getQuestionFlow();
        assertThat(flow).hasSize(3);
        assertThat(flow.get(0).getType()).isEqualTo("preset");
        assertThat(flow.get(1).getText()).isEqualTo("Hello");
        assertThat(flow.get(2).getText()).isEqualTo("Another question");

        assertThat(sanitized.getActiveTemplateId()).isEqualTo("q-1");

        List<RatingTemplateDto> ratingTemplates = sanitized.getRatingTemplates();
        assertThat(ratingTemplates).hasSize(2);
        RatingTemplateDto primary = ratingTemplates.get(0);
        assertThat(primary.getId()).isEqualTo("rate1");
        assertThat(primary.getScaleSize()).isEqualTo(7);
        assertThat(primary.getPromptText()).contains("1 до 7");
        assertThat(primary.getResponses()).hasSize(7);

        assertThat(sanitized.getRatingSystem().getScaleSize()).isEqualTo(7);
        assertThat(sanitized.getRatingSystem().getResponses()).hasSize(7);
        assertThat(sanitized.getRatingSystem().getResponses().get(0).getText()).isEqualTo("bad");

        Set<String> allowedRatings = service.ratingAllowedValues(sanitized);
        assertThat(allowedRatings).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(service.ratingResponseFor(sanitized, "1")).contains("bad");
        assertThat(service.ratingResponseFor(sanitized, 2)).isPresent();
    }

    @Test
    void buildLocationPresetsShouldMatchExpectedOutput() throws IOException {
        Map<String, Object> locationTree = objectMapper.readValue(
                """
                        {
                          "Business A": {
                            "Cafe": {
                              "Moscow": ["Tverskaya", "Arbat"],
                              "Berlin": ["Mitte"]
                            },
                            "Store": {
                              "Moscow": ["Center"]
                            }
                          },
                          "Business B": {
                            "Store": {
                              "Berlin": ["Alexanderplatz"]
                            }
                          }
                        }
                        """,
                new TypeReference<>() {
                });

        Map<String, Object> javaResult = service.buildLocationPresets(locationTree);

        Map<String, Object> pythonResult = objectMapper.readValue(
                """
                        {
                          "locations": {
                            "label": "Структура локаций",
                            "fields": {
                              "business": {
                                "label": "Бизнес",
                                "options": ["Business A", "Business B"]
                              },
                              "location_type": {
                                "label": "Тип бизнеса",
                                "options": ["Cafe", "Store"],
                                "option_dependencies": {
                                  "Cafe": {"business": ["Business A"]},
                                  "Store": {"business": ["Business A", "Business B"]}
                                },
                                "tree": {
                                  "Business A": ["Cafe", "Store"],
                                  "Business B": ["Store"]
                                }
                              },
                              "city": {
                                "label": "Город",
                                "options": ["Berlin", "Moscow"],
                                "option_dependencies": {
                                  "Berlin": {
                                    "business": ["Business A", "Business B"],
                                    "location_type": ["Cafe", "Store"],
                                    "paths": [
                                      {"business": "Business A", "location_type": "Cafe"},
                                      {"business": "Business B", "location_type": "Store"}
                                    ]
                                  },
                                  "Moscow": {
                                    "business": ["Business A"],
                                    "location_type": ["Cafe", "Store"],
                                    "paths": [
                                      {"business": "Business A", "location_type": "Cafe"},
                                      {"business": "Business A", "location_type": "Store"}
                                    ]
                                  }
                                },
                                "tree": {
                                  "Business A": {
                                    "Cafe": ["Berlin", "Moscow"],
                                    "Store": ["Moscow"]
                                  },
                                  "Business B": {
                                    "Store": ["Berlin"]
                                  }
                                }
                              },
                              "location_name": {
                                "label": "Локация",
                                "options": ["Alexanderplatz", "Arbat", "Center", "Mitte", "Tverskaya"],
                                "option_dependencies": {
                                  "Alexanderplatz": {
                                    "business": ["Business B"],
                                    "location_type": ["Store"],
                                    "city": ["Berlin"],
                                    "paths": [
                                      {"business": "Business B", "location_type": "Store", "city": "Berlin"}
                                    ]
                                  },
                                  "Arbat": {
                                    "business": ["Business A"],
                                    "location_type": ["Cafe"],
                                    "city": ["Moscow"],
                                    "paths": [
                                      {"business": "Business A", "location_type": "Cafe", "city": "Moscow"}
                                    ]
                                  },
                                  "Center": {
                                    "business": ["Business A"],
                                    "location_type": ["Store"],
                                    "city": ["Moscow"],
                                    "paths": [
                                      {"business": "Business A", "location_type": "Store", "city": "Moscow"}
                                    ]
                                  },
                                  "Mitte": {
                                    "business": ["Business A"],
                                    "location_type": ["Cafe"],
                                    "city": ["Berlin"],
                                    "paths": [
                                      {"business": "Business A", "location_type": "Cafe", "city": "Berlin"}
                                    ]
                                  },
                                  "Tverskaya": {
                                    "business": ["Business A"],
                                    "location_type": ["Cafe"],
                                    "city": ["Moscow"],
                                    "paths": [
                                      {"business": "Business A", "location_type": "Cafe", "city": "Moscow"}
                                    ]
                                  }
                                },
                                "tree": {
                                  "Business A": {
                                    "Cafe": {
                                      "Berlin": ["Mitte"],
                                      "Moscow": ["Arbat", "Tverskaya"]
                                    },
                                    "Store": {
                                      "Moscow": ["Center"]
                                    }
                                  },
                                  "Business B": {
                                    "Store": {
                                      "Berlin": ["Alexanderplatz"]
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                        """,
                new TypeReference<>() {
                });

        assertThat(javaResult).isEqualTo(pythonResult);
    }
}

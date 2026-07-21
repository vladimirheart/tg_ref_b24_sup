package com.example.supportbot.settings;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.RatingResponseDto;
import com.example.supportbot.settings.dto.RatingSystemDto;
import com.example.supportbot.settings.dto.RatingTemplateDto;
import com.example.supportbot.service.SharedConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BotSettingsServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    private ObjectMapper objectMapper;
    private BotSettingsService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, tempDir.toString());
        service = new BotSettingsService(objectMapper, sharedConfigService);
    }

    @Test
    void defaultSettingsShouldExposePresetQuestionsAndRatings() {
        BotSettingsDto defaults = service.buildDefaultSettings();

        assertThat(defaults.getQuestionTemplates()).hasSize(1);
        assertThat(defaults.getQuestionFlow()).hasSize(4);
        assertThat(defaults.getRatingTemplates()).hasSize(1);
        assertThat(defaults.getRatingSystem().getScaleSize()).isEqualTo(5);
        assertThat(defaults.getRatingSystem().getResponses())
                .extracting(com.example.supportbot.settings.dto.RatingResponseDto::getText)
                .allSatisfy(text -> assertThat(text).isNotBlank());
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
        assertThat(flow.get(1).isRequiredAnswer()).isTrue();
        assertThat(flow.get(2).isRequiredAnswer()).isTrue();

        assertThat(sanitized.getActiveTemplateId()).isEqualTo("q-1");

        List<RatingTemplateDto> ratingTemplates = sanitized.getRatingTemplates();
        assertThat(ratingTemplates).hasSize(2);
        RatingTemplateDto primary = ratingTemplates.get(0);
        assertThat(primary.getId()).isEqualTo("rate1");
        assertThat(primary.getScaleSize()).isEqualTo(7);
        assertThat(primary.getPromptText()).contains("7");
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
    void sanitizeShouldPreserveRequiredFlagForOptionalFreeQuestions() throws IOException {
        Map<String, Object> raw = objectMapper.readValue(
                """
                        {
                          "question_templates": [
                            {
                              "id": "template-optional",
                              "name": "Optional flow",
                              "question_flow": [
                                {"id": "q-optional", "type": "custom", "text": "Optional question", "required": false},
                                {"id": "q-required", "type": "custom", "text": "Required question", "required": "1"},
                                {"id": "q-preset", "type": "preset", "preset": {"group": "locations", "field": "city"}, "required": 0}
                              ]
                            }
                          ],
                          "active_template_id": "template-optional"
                        }
                        """,
                new TypeReference<>() {
                });

        BotSettingsDto sanitized = service.sanitizeFromJson(raw);

        assertThat(sanitized.getQuestionTemplates()).hasSize(1);
        List<QuestionFlowItemDto> flow = sanitized.getQuestionTemplates().get(0).getQuestionFlow();
        assertThat(flow).hasSize(3);
        assertThat(flow.get(0).getText()).isEqualTo("Optional question");
        assertThat(flow.get(0).isRequiredAnswer()).isFalse();
        assertThat(flow.get(1).getText()).isEqualTo("Required question");
        assertThat(flow.get(1).isRequiredAnswer()).isTrue();
        assertThat(flow.get(2).getType()).isEqualTo("preset");
        assertThat(flow.get(2).getPreset()).isNotNull();
        assertThat(flow.get(2).getRequired()).isFalse();

        assertThat(sanitized.getQuestionFlow()).extracting(QuestionFlowItemDto::getRequired)
                .containsExactly(false, true, false);
    }

    @Test
    void sanitizeShouldImportLegacyQuestionFlowAndRatingSystemIntoCanonicalTemplates() throws IOException {
        Map<String, Object> raw = objectMapper.readValue(
                """
                        {
                          "question_flow": [
                            {"type": "custom", "text": "Как вас зовут?"},
                            {"type": "preset", "group": "locations", "field": "city"}
                          ],
                          "rating_system": {
                            "scale_size": 5,
                            "prompt_text": "Оцените консультацию",
                            "responses": {
                              "1": "Плохо",
                              "5": "Отлично"
                            }
                          }
                        }
                        """,
                new TypeReference<>() {
                });

        BotSettingsDto sanitized = service.sanitizeFromJson(raw);

        assertThat(sanitized.getQuestionTemplates()).hasSize(1);
        assertThat(sanitized.getQuestionTemplates().get(0).getQuestionFlow()).hasSize(2);
        assertThat(sanitized.getQuestionFlow()).extracting(QuestionFlowItemDto::getText)
                .containsExactly("Как вас зовут?", "Город");

        assertThat(sanitized.getRatingTemplates()).hasSize(1);
        assertThat(sanitized.getRatingSystem().getPromptText()).isEqualTo("Оцените консультацию");
        assertThat(sanitized.getRatingSystem().getResponses())
                .extracting(com.example.supportbot.settings.dto.RatingResponseDto::getText)
                .contains("Плохо", "Отлично");
    }

    @Test
    void ratingHelpersShouldPreferActiveRatingTemplateOverLegacyRootMirror() {
        BotSettingsDto settings = new BotSettingsDto();
        settings.setRatingTemplates(List.of(
                new RatingTemplateDto(
                        "rate-template-active",
                        "Active template",
                        null,
                        "Template prompt",
                        3,
                        List.of(
                                new RatingResponseDto(1, "Template bad"),
                                new RatingResponseDto(3, "Template great")
                        )
                )
        ));
        settings.setActiveRatingTemplateId("rate-template-active");
        settings.setRatingSystem(new RatingSystemDto(
                "Legacy prompt",
                5,
                List.of(
                        new RatingResponseDto(1, "Legacy bad"),
                        new RatingResponseDto(5, "Legacy great")
                )
        ));

        assertThat(service.ratingScale(settings, 5)).isEqualTo(3);
        assertThat(service.ratingPrompt(settings, null)).isEqualTo("Template prompt");
        assertThat(service.ratingResponses(settings))
                .containsEntry("1", "Template bad")
                .containsEntry("3", "Template great")
                .doesNotContainValue("Legacy great");
    }

    @Test
    void questionFlowHelperShouldPreferActiveQuestionTemplateOverLegacyRootMirror() {
        BotSettingsDto settings = new BotSettingsDto();
        settings.setQuestionTemplates(List.of(
                new com.example.supportbot.settings.dto.QuestionTemplateDto(
                        "question-template-active",
                        "Active template",
                        null,
                        null,
                        List.of(
                                new QuestionFlowItemDto("q-template", "custom", "Template question", 1, null, List.of())
                        )
                )
        ));
        settings.setActiveTemplateId("question-template-active");
        settings.setQuestionFlow(List.of(
                new QuestionFlowItemDto("q-legacy", "custom", "Legacy question", 1, null, List.of())
        ));

        assertThat(service.questionFlow(settings))
                .extracting(QuestionFlowItemDto::getText)
                .containsExactly("Template question");
    }

    @Test
    void dtoShouldSerializeDerivedCompatibilityMirrorsFromActiveTemplates() throws IOException {
        Map<String, Object> raw = objectMapper.readValue(
                """
                        {
                          "question_templates": [
                            {
                              "id": "q-active",
                              "name": "Active",
                              "question_flow": [
                                {"type": "custom", "text": "Derived question"}
                              ]
                            }
                          ],
                          "active_template_id": "q-active",
                          "rating_templates": [
                            {
                              "id": "r-active",
                              "name": "Active rating",
                              "scale_size": 3,
                              "prompt_text": "Derived prompt",
                              "responses": {
                                "1": "Bad",
                                "3": "Great"
                              }
                            }
                          ],
                          "active_rating_template_id": "r-active"
                        }
                        """,
                new TypeReference<>() {
                });

        BotSettingsDto sanitized = service.sanitizeFromJson(raw);
        Map<String, Object> serialized = objectMapper.convertValue(sanitized, new TypeReference<>() {
        });

        assertThat(((List<?>) serialized.get("question_flow"))).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) serialized.get("question_flow")).get(0)).get("text"))
                .isEqualTo("Derived question");
        assertThat(((Map<?, ?>) serialized.get("rating_system")).get("prompt_text"))
                .isEqualTo("Derived prompt");
        assertThat(((Map<?, ?>) serialized.get("rating_system")).get("scale_size"))
                .isEqualTo(3);
    }

    @Test
    void loadFromChannelShouldApplyTypedTemplateOverridesOnly() throws IOException {
        writeSharedSettings("""
                {
                  "bot_settings": {
                    "question_templates": [
                      {
                        "id": "q-default",
                        "name": "Default",
                        "question_flow": [{"type": "custom", "text": "Default question"}]
                      },
                      {
                        "id": "q-channel",
                        "name": "Channel",
                        "question_flow": [{"type": "custom", "text": "Channel question"}]
                      }
                    ],
                    "active_template_id": "q-default",
                    "rating_templates": [
                      {
                        "id": "rate-default",
                        "name": "Default rating",
                        "scale_size": 5,
                        "prompt_text": "Default prompt",
                        "responses": {
                          "1": "Default bad",
                          "5": "Default good"
                        }
                      },
                      {
                        "id": "rate-channel",
                        "name": "Channel rating",
                        "scale_size": 3,
                        "prompt_text": "Channel prompt",
                        "responses": {
                          "1": "Channel bad",
                          "3": "Channel great"
                        }
                      }
                    ],
                    "active_rating_template_id": "rate-default"
                  }
                }
                """);
        Channel channel = new Channel();
        channel.setId(77L);
        channel.setQuestionTemplateId("q-channel");
        channel.setRatingTemplateId("rate-channel");
        channel.setQuestionsCfg("""
                {
                  "active_template_id": "q-default",
                  "active_rating_template_id": "rate-default"
                }
                """);

        BotSettingsDto settings = service.loadFromChannel(channel);

        assertThat(settings.getActiveTemplateId()).isEqualTo("q-channel");
        assertThat(settings.getQuestionFlow()).extracting(QuestionFlowItemDto::getText)
                .containsExactly("Channel question");
        assertThat(settings.getActiveRatingTemplateId()).isEqualTo("rate-channel");
        assertThat(settings.getRatingSystem().getPromptText()).isEqualTo("Channel prompt");
        assertThat(settings.getRatingSystem().getScaleSize()).isEqualTo(3);
    }

    @Test
    void loadFromChannelShouldIgnoreLegacyQuestionsCfgTemplateSelectionWhenTypedOverridesAreBlank() throws IOException {
        writeSharedSettings("""
                {
                  "bot_settings": {
                    "question_templates": [
                      {
                        "id": "q-default",
                        "name": "Default",
                        "question_flow": [{"type": "custom", "text": "Default question"}]
                      },
                      {
                        "id": "q-legacy",
                        "name": "Legacy",
                        "question_flow": [{"type": "custom", "text": "Legacy question"}]
                      }
                    ],
                    "active_template_id": "q-default",
                    "rating_templates": [
                      {
                        "id": "rate-default",
                        "name": "Default rating",
                        "scale_size": 5,
                        "prompt_text": "Default prompt",
                        "responses": {
                          "1": "Default bad",
                          "5": "Default good"
                        }
                      },
                      {
                        "id": "rate-legacy",
                        "name": "Legacy rating",
                        "scale_size": 4,
                        "prompt_text": "Legacy prompt",
                        "responses": {
                          "1": "Legacy bad",
                          "4": "Legacy great"
                        }
                      }
                    ],
                    "active_rating_template_id": "rate-default"
                  }
                }
                """);
        Channel channel = new Channel();
        channel.setId(88L);
        channel.setQuestionsCfg("""
                {
                  "question_template_id": "q-legacy",
                  "rating_template_id": "rate-legacy"
                }
                """);

        BotSettingsDto settings = service.loadFromChannel(channel);

        assertThat(settings.getActiveTemplateId()).isEqualTo("q-default");
        assertThat(settings.getQuestionFlow()).extracting(QuestionFlowItemDto::getText)
                .containsExactly("Default question");
        assertThat(settings.getActiveRatingTemplateId()).isEqualTo("rate-default");
        assertThat(settings.getRatingSystem().getPromptText()).isEqualTo("Default prompt");
        assertThat(settings.getRatingSystem().getScaleSize()).isEqualTo(5);
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

    private void writeSharedSettings(String json) throws IOException {
        Files.writeString(tempDir.resolve("settings.json"), json);
    }
}

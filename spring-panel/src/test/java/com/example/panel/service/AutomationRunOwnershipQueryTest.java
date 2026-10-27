package com.example.panel.service;

import com.example.panel.entity.AutomationRun;
import com.example.panel.repository.AutomationRunRepository;
import jakarta.persistence.Column;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.*;

/** Exercise Spring Data's property parser without a database; PostgreSQL execution is separate. */
class AutomationRunOwnershipQueryTest {
    @Test void listQueryUsesOwnerIdentityAndHasDeterministicTopTwentyOrdering() {
        String name="findTop20ByAutomationKeyAndActorUserIdOrderByStartedAtDescIdDesc";
        assertThat(Arrays.stream(AutomationRunRepository.class.getMethods()).map(m->m.getName()).toList()).contains(name);
        var tree=new PartTree(name,AutomationRun.class);assertThat(tree.getMaxResults()).isEqualTo(20);
        assertThat(tree.getParts().stream().map(part->part.getProperty().toDotPath()).toList()).containsExactly("automationKey","actorUserId");
        assertThat(tree.getSort().stream().map(order->order.getProperty()+":"+order.getDirection()).toList()).containsExactly("startedAt:DESC","id:DESC");
    }
    @Test void detailQueryRequiresRunIdAutomationAndOwnerId() {
        String name="findByIdAndAutomationKeyAndActorUserId";
        assertThat(Arrays.stream(AutomationRunRepository.class.getMethods()).map(m->m.getName()).toList()).contains(name);
        var tree=new PartTree(name,AutomationRun.class);
        assertThat(tree.getParts().stream().map(part->part.getProperty().toDotPath()).toList()).containsExactly("id","automationKey","actorUserId");
        assertThat(tree.isLimiting()).isFalse();
    }
    @Test void ownerIdIsMappedToItsOwnBigintColumnAndDisplayActorRemainsSeparate() {
        var fields=Arrays.stream(AutomationRun.class.getDeclaredFields()).filter(f->f.getName().equals("actorUserId")).toList();
        assertThat(fields).hasSize(1);var field=fields.get(0);assertThat(field.getType()).isEqualTo(Long.class);
        assertThat(field.getAnnotation(Column.class)).isNotNull();assertThat(field.getAnnotation(Column.class).name()).isEqualTo("actor_user_id");
        assertThat(Arrays.stream(AutomationRun.class.getDeclaredFields()).filter(f->f.getName().equals("actor")).map(f->f.getType()).toList()).isEqualTo(List.of(String.class));
    }
}

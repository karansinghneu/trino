/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.sql.planner.iterative.rule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.prestosql.sql.planner.iterative.rule.test.BaseRuleTest;
import io.prestosql.sql.planner.iterative.rule.test.PlanBuilder;
import io.prestosql.sql.planner.plan.CorrelatedJoinNode;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.prestosql.sql.planner.assertions.PlanMatchPattern.aggregation;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.assignUniqueId;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.expression;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.filter;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.join;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.project;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.singleGroupingSet;
import static io.prestosql.sql.planner.assertions.PlanMatchPattern.values;
import static io.prestosql.sql.planner.plan.AggregationNode.Step.SINGLE;
import static io.prestosql.sql.planner.plan.JoinNode.Type.LEFT;

public class TestTransformCorrelatedDistinctAggregationWithoutProjection
        extends BaseRuleTest
{
    @Test
    public void doesNotFireOnUncorrelated()
    {
        tester().assertThat(new TransformCorrelatedDistinctAggregationWithoutProjection(tester().getMetadata()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(),
                        p.values(p.symbol("a")),
                        p.values(p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void doesNotFireOnCorrelatedWithNonDistinctAggregation()
    {
        tester().assertThat(new TransformCorrelatedDistinctAggregationWithoutProjection(tester().getMetadata()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        p.aggregation(ab -> ab
                                .source(p.values(p.symbol("a"), p.symbol("b")))
                                .singleGroupingSet(p.symbol("b")))))
                .doesNotFire();
    }

    @Test
    public void rewritesOnSubqueryWithDistinct()
    {
        tester().assertThat(new TransformCorrelatedDistinctAggregationWithoutProjection(tester().getMetadata()))
                .on(p -> p.correlatedJoin(
                        ImmutableList.of(p.symbol("corr")),
                        p.values(p.symbol("corr")),
                        CorrelatedJoinNode.Type.LEFT,
                        PlanBuilder.expression("true"),
                        p.aggregation(innerBuilder -> innerBuilder
                                .singleGroupingSet(p.symbol("a"))
                                .source(p.filter(
                                        PlanBuilder.expression("b > corr"),
                                        p.values(p.symbol("a"), p.symbol("b")))))))
                .matches(
                        project(ImmutableMap.of("corr", expression("corr"), "a", expression("a")),
                                aggregation(
                                        singleGroupingSet("corr", "unique", "a"),
                                        ImmutableMap.of(),
                                        Optional.empty(),
                                        SINGLE,
                                        join(
                                                LEFT,
                                                ImmutableList.of(),
                                                Optional.of("b > corr"),
                                                assignUniqueId(
                                                        "unique",
                                                        values("corr")),
                                                filter(
                                                        "true",
                                                        values("a", "b"))))));
    }
}
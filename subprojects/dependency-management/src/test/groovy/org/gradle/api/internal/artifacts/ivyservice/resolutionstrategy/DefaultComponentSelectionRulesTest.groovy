/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.RuleActionAdapter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification

class DefaultComponentSelectionRulesTest extends Specification {
    RuleActionAdapter<ComponentSelection> adapter = Mock(RuleActionAdapter)
    NotationParser<Object, String> notationParser = Mock(NotationParser)
    ComponentSelectionRulesInternal rules = new DefaultComponentSelectionRules(adapter, notationParser)
    ComponentSelectionInternal componentSelection
    def ruleAction = Mock(RuleAction)

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        componentSelection = new DefaultComponentSelection(componentIdentifier)
    }

    def "add closure rule that applies to all components"() {
        def input = { ComponentSelection cs ->  }

        when:
        rules.all input

        then:
        1 * adapter.createFromClosure(ComponentSelection, input) >> ruleAction

        and:
        rules.rules.size() == 1
        rules.rules[0].action == ruleAction
        rules.rules[0].spec == Specs.satisfyAll()
    }

    def "add closure rule that applies to module"() {
        def input = { ComponentSelection cs ->  }
        def group = "group"
        def module = "module"
        def notation = "${group}:${module}"

        when:
        rules.module(notation, input)

        then:
        1 * adapter.createFromClosure(ComponentSelection, input) >> ruleAction
        1 * notationParser.parseNotation(notation) >> DefaultModuleIdentifier.newId(group, module)

        and:
        rules.rules.size() == 1
        rules.rules[0].action == ruleAction
        rules.rules[0].spec.target == DefaultModuleIdentifier.newId("group", "module")
    }

    def "add action rule that applies to all components"() {
        def Action<ComponentSelection> action = Mock(Action)

        when:
        rules.all action

        then:
        1 * adapter.createFromAction(action) >> ruleAction

        and:
        rules.rules.size() == 1
        rules.rules[0].action == ruleAction
        rules.rules[0].spec == Specs.satisfyAll()
    }

    def "add action rule that applies to module"() {
        def Action<ComponentSelection> action = Mock(Action)
        def group = "group"
        def module = "module"
        def notation = "${group}:${module}"

        when:
        rules.module(notation, action)

        then:
        1 * adapter.createFromAction(action) >> ruleAction
        1 * notationParser.parseNotation(notation) >> DefaultModuleIdentifier.newId(group, module)

        and:
        rules.rules.size() == 1
        rules.rules[0].action == ruleAction
        rules.rules[0].spec.target == DefaultModuleIdentifier.newId("group", "module")
    }

    def "propagates error creating rule for closure" () {
        when:
        rules.all { }

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "bad closure"

        and:
        1 * adapter.createFromClosure(ComponentSelection, _) >> { throw new InvalidUserCodeException("bad closure") }

        when:
        rules.module("group:module") { }

        then:
        e = thrown(InvalidUserCodeException)
        e.message == "bad targeted closure"

        and:
        1 * adapter.createFromClosure(ComponentSelection, _) >> { throw new InvalidUserCodeException("bad targeted closure") }
    }

    def "propagates error creating rule for action" () {
        def action = Mock(Action)

        when:
        rules.all action

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "bad action"

        and:
        1 * adapter.createFromAction(action) >> { throw new InvalidUserCodeException("bad action") }

        when:
        rules.module("group:module", action)

        then:
        e = thrown(InvalidUserCodeException)
        e.message == "bad targeted action"

        and:
        1 * adapter.createFromAction(action) >> { throw new InvalidUserCodeException("bad targeted action") }
    }

    def "propagates error parsing module identifier for closure" () {
        def input = { ComponentSelection cs -> }
        def notation = "group:module:1.0"

        when:
        rules.module(notation, input)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not add a component selection rule for module '${notation}'."
        def cause = e.cause
        cause instanceof UnsupportedNotationException
        cause.notation == notation

        and:
        1 * notationParser.parseNotation(notation) >> { throw new UnsupportedNotationException(notation) }
    }

    def "propagates error parsing module identifier for action" () {
        def input = Mock(Action)
        def notation = "group:module:1.0"

        when:
        rules.module(notation, input)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not add a component selection rule for module '${notation}'."
        def cause = e.cause
        cause instanceof UnsupportedNotationException
        cause.notation == notation

        and:
        1 * notationParser.parseNotation(notation) >> { throw new UnsupportedNotationException(notation) }
    }

    def "ComponentSelectionSpec matches on group and name" () {
        def spec = new DefaultComponentSelectionRules.ComponentSelectionMatchingSpec(DefaultModuleIdentifier.newId(group, name))
        def candidate = Mock(ModuleComponentIdentifier) {
            1 * getGroup() >> "org.gradle"
            (0..1) * getModule() >> "api"
        }
        def selection = Stub(ComponentSelection) {
            getCandidate() >> candidate
        }

        expect:
        spec.isSatisfiedBy(selection) == matches

        where:
        group        | name  | matches
        "org.gradle" | "api" | true
        "org.gradle" | "lib" | false
        "com.gradle" | "api" | false
    }

    private class TestComponentSelectionAction implements Action<ComponentSelection> {
        boolean called = false

        @Override
        void execute(ComponentSelection componentSelection) {
            called = true
        }
    }
}

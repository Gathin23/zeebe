/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ZeebeExpressionValidator.ExpressionVerification;
import io.camunda.zeebe.model.bpmn.instance.ConditionExpression;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public final class ZeebeRuntimeValidators {

  public static Collection<ModelElementValidator<?>> getValidators(
      final ExpressionLanguage expressionLanguage, final ExpressionProcessor expressionProcessor) {
    return List.of(
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeInput.class)
            .hasValidExpression(
                ZeebeInput::getSource, expression -> expression.isNonStatic().isMandatory())
            .hasValidPath(ZeebeInput::getTarget)
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeOutput.class)
            .hasValidExpression(
                ZeebeOutput::getSource, expression -> expression.isNonStatic().isMandatory())
            .hasValidPath(ZeebeOutput::getTarget)
            .build(expressionLanguage),
        ZeebeExpressionValidator.verifyThat(Message.class)
            .hasValidExpression(Message::getName, expression -> expression.isOptional())
            .build(expressionLanguage),
        // Checks message name expressions of start event messages
        new ProcessMessageStartEventMessageNameValidator(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeSubscription.class)
            .hasValidExpression(
                ZeebeSubscription::getCorrelationKey,
                expression -> expression.isNonStatic().isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeLoopCharacteristics.class)
            .hasValidExpression(
                ZeebeLoopCharacteristics::getInputCollection,
                expression -> expression.isNonStatic().isMandatory())
            .hasValidExpression(
                ZeebeLoopCharacteristics::getOutputElement,
                expression -> expression.isNonStatic().isOptional())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ConditionExpression.class)
            .hasValidExpression(
                ConditionExpression::getTextContent,
                expression -> expression.isNonStatic().isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeTaskDefinition.class)
            .hasValidExpression(
                ZeebeTaskDefinition::getType, expression -> expression.isMandatory())
            .hasValidExpression(
                ZeebeTaskDefinition::getRetries, expression -> expression.isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeCalledElement.class)
            .hasValidExpression(
                ZeebeCalledElement::getProcessId, expression -> expression.isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(TimerEventDefinition.class)
            .hasValidExpression(
                definition ->
                    definition.getTimeDate() != null
                        ? definition.getTimeDate().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .hasValidExpression(
                definition ->
                    definition.getTimeDuration() != null
                        ? definition.getTimeDuration().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .hasValidExpression(
                definition ->
                    definition.getTimeCycle() != null
                        ? definition.getTimeCycle().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeAssignmentDefinition.class)
            .hasValidExpression(
                ZeebeAssignmentDefinition::getAssignee, ExpressionVerification::isOptional)
            .hasValidExpression(
                ZeebeAssignmentDefinition::getCandidateGroups,
                expression ->
                    expression
                        .isOptional()
                        .satisfiesIfStatic(
                            ZeebeExpressionValidator::isListOfCsv,
                            "be a list of comma-separated values, e.g. 'a,b,c'"))
            .build(expressionLanguage),
        // ----------------------------------------
        new TimerCatchEventExpressionValidator(expressionLanguage, expressionProcessor),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeCalledDecision.class)
            .hasValidExpression(
                ZeebeCalledDecision::getDecisionId, ExpressionVerification::isMandatory)
            .build(expressionLanguage),
        // ----------------------------------------
        new ZeebeTaskHeadersValidator(),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(MultiInstanceLoopCharacteristics.class)
            .hasValidExpression(
                loopCharacteristics ->
                    loopCharacteristics.getCompletionCondition() != null
                        ? loopCharacteristics.getCompletionCondition().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .build(expressionLanguage));
  }
}

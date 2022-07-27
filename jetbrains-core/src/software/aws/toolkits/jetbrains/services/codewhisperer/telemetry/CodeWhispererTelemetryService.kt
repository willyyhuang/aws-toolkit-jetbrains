// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.codewhisperer.model.Recommendation
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.model.ProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.time.Instant

class CodeWhispererTelemetryService {
    companion object {
        fun getInstance(): CodeWhispererTelemetryService = service()
        val LOG = getLogger<CodeWhispererTelemetryService>()
    }

    fun sendFailedServiceInvocationEvent(project: Project, exceptionType: String?) {
        CodewhispererTelemetry.serviceInvocation(
            project = project,
            codewhispererCursorOffset = 0,
            codewhispererLanguage = CodewhispererLanguage.Unknown,
            codewhispererLastSuggestionIndex = -1,
            codewhispererLineNumber = 0,
            codewhispererTriggerType = CodewhispererTriggerType.Unknown,
            duration = 0.0,
            reason = exceptionType,
            success = false
        )
    }

    fun sendServiceInvocationEvent(
        requestId: String,
        requestContext: RequestContext,
        responseContext: ResponseContext,
        lastRecommendationIndex: Int,
        invocationSuccess: Boolean,
        latency: Double,
        exceptionType: String?
    ) {
        val (project, _, triggerTypeInfo, caretPosition) = requestContext
        val (triggerType, automatedTriggerType) = triggerTypeInfo
        val (offset, line) = caretPosition
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toCodeWhispererLanguage()
        CodewhispererTelemetry.serviceInvocation(
            project = project,
            codewhispererAutomatedTriggerType = automatedTriggerType,
            codewhispererCompletionType = responseContext.completionType,
            codewhispererCursorOffset = offset,
            codewhispererLanguage = codewhispererLanguage,
            codewhispererLastSuggestionIndex = lastRecommendationIndex,
            codewhispererLineNumber = line,
            codewhispererRequestId = requestId,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererTriggerType = triggerType,
            duration = latency,
            reason = exceptionType,
            success = invocationSuccess
        )
    }

    private fun sendUserDecisionEvent(
        requestId: String,
        requestContext: RequestContext,
        responseContext: ResponseContext,
        detail: Recommendation,
        index: Int,
        suggestionState: CodewhispererSuggestionState,
        numOfRecommendations: Int
    ) {
        val (project, _, triggerTypeInfo) = requestContext
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toCodeWhispererLanguage()

        LOG.debug {
            "Recording user decisions of recommendation. " +
                "Index: $index, " +
                "State: $suggestionState, " +
                "Request ID: $requestId, " +
                "Recommendation: ${detail.content()}"
        }
        CodewhispererTelemetry.userDecision(
            project = project,
            codewhispererCompletionType = responseContext.completionType,
            codewhispererLanguage = codewhispererLanguage,
            codewhispererPaginationProgress = numOfRecommendations,
            codewhispererRequestId = requestId,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererSuggestionIndex = index,
            codewhispererSuggestionReferenceCount = detail.references().size,
            codewhispererSuggestionReferences = jacksonObjectMapper().writeValueAsString(detail.references().map { it.licenseName() }.toSet().toList()),
            codewhispererSuggestionState = suggestionState,
            codewhispererTriggerType = triggerTypeInfo.triggerType
        )
    }

    fun sendSecurityScanEvent(codeScanEvent: CodeScanTelemetryEvent, project: Project? = null) {
        val (jobId, language, payloadSize, codeScanLines, codeScanIssues, reason) = codeScanEvent.codeScanResponseContext

        LOG.debug {
            "Recording code security scan event. " +
                "Security scan job id: $jobId, " +
                "Language: $language, " +
                "Payload size: $payloadSize, " +
                "Total number of lines scanned: $codeScanLines, " +
                "Total number of security scan issues found: $codeScanIssues " +
                "Total duration of the security scan job: ${codeScanEvent.duration} " +
                "Reason: $reason"
        }
        CodewhispererTelemetry.securityScan(
            project = project,
            codewhispererCodeScanLines = codeScanLines.toInt(),
            codewhispererCodeScanJobId = jobId,
            codewhispererCodeScanPayloadBytes = payloadSize.toInt(),
            codewhispererCodeScanTotalIssues = codeScanIssues,
            codewhispererLanguage = language,
            duration = codeScanEvent.duration,
            reason = reason,
            result = codeScanEvent.result
        )
    }

    fun enqueueAcceptedSuggestionEntry(
        requestId: String,
        requestContext: RequestContext,
        responseContext: ResponseContext,
        time: Instant,
        vFile: VirtualFile?,
        range: RangeMarker,
        suggestion: String,
        selectedIndex: Int
    ) {
        val (project, _, triggerTypeInfo) = requestContext
        val (sessionId, completionType) = responseContext
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toCodeWhispererLanguage()
        CodeWhispererUserModificationTracker.getInstance(project).enqueue(
            AcceptedSuggestionEntry(
                time, vFile, range, suggestion, sessionId, requestId, selectedIndex,
                triggerTypeInfo.triggerType, completionType,
                codewhispererLanguage, null, null
            )
        )
    }

    fun sendUserDecisionEventForAll(
        requestContext: RequestContext,
        responseContext: ResponseContext,
        recommendationContext: RecommendationContext,
        sessionContext: SessionContext,
        hasUserAccepted: Boolean,
    ) {
        val detailContexts = recommendationContext.details
        detailContexts.forEachIndexed { index, detailContext ->
            val (requestId, detail, _, isDiscarded) = detailContext
            val suggestionState = recordSuggestionState(
                index,
                sessionContext.selectedIndex,
                sessionContext.seen.contains(index),
                hasUserAccepted,
                isDiscarded,
                detail.hasReferences()
            )
            sendUserDecisionEvent(requestId, requestContext, responseContext, detail, index, suggestionState, detailContexts.size)
        }
    }

    private fun recordSuggestionState(
        index: Int,
        selectedIndex: Int,
        hasSeen: Boolean,
        hasUserAccepted: Boolean,
        isDiscarded: Boolean,
        hasReference: Boolean
    ): CodewhispererSuggestionState =
        if (!CodeWhispererSettings.getInstance().isIncludeCodeWithReference() && hasReference) {
            CodewhispererSuggestionState.Filter
        } else if (isDiscarded) {
            CodewhispererSuggestionState.Discard
        } else if (!hasSeen) {
            CodewhispererSuggestionState.Unseen
        } else if (hasUserAccepted) {
            if (selectedIndex == index) {
                CodewhispererSuggestionState.Accept
            } else {
                CodewhispererSuggestionState.Ignore
            }
        } else {
            CodewhispererSuggestionState.Reject
        }
}

fun ProgrammingLanguage.toCodeWhispererLanguage() = when (languageName) {
    CodewhispererLanguage.Python.toString() -> CodewhispererLanguage.Python
    CodewhispererLanguage.Java.toString() -> CodewhispererLanguage.Java
    CodewhispererLanguage.Javascript.toString() -> CodewhispererLanguage.Javascript
    "plain_text" -> CodewhispererLanguage.Plaintext
    else -> CodewhispererLanguage.Unknown
}

fun isTelemetryEnabled(): Boolean = AwsSettings.getInstance().isTelemetryEnabled
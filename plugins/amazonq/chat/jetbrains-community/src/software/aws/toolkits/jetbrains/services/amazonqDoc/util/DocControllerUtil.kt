// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.util

import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpIcons
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.resources.message

fun getFollowUpOptions(phase: SessionStatePhase?): List<FollowUp> {
    when (phase) {
        SessionStatePhase.CODEGEN -> {
            return listOf(
                FollowUp(
                    pillText = message("amazonqDoc.follow_up.insert_code"),
                    type = FollowUpTypes.INSERT_CODE,
                    icon = FollowUpIcons.Ok,
                    status = FollowUpStatusType.Success
                ),
                FollowUp(
                    pillText = message("amazonqDoc.follow_up.provide_feedback_and_regenerate"),
                    type = FollowUpTypes.PROVIDE_FEEDBACK_AND_REGENERATE_CODE,
                    icon = FollowUpIcons.Refresh,
                    status = FollowUpStatusType.Info
                )
            )
        }
        else -> return emptyList()
    }
}

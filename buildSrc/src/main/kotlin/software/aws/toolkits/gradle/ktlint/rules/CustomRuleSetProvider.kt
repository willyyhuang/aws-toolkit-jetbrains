// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.ktlint.rules

import com.pinterest.ktlint.core.RuleSet
import com.pinterest.ktlint.core.RuleSetProvider

class CustomRuleSetProvider : RuleSetProvider {
    override fun get() = RuleSet(
        "custom-ktlint-rules",
        CopyrightHeaderRule(),
        BannedPatternRule(BannedPatternRule.DEFAULT_PATTERNS),
        ExpressionBodyRule(),
        LazyLogRule(),
        DialogModalityRule(),
        BannedImportsRule()
    )
}
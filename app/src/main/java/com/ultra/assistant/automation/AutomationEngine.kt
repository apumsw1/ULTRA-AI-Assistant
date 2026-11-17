package com.ultra.assistant.automation
/** Holds the local rule store boundary; scheduled execution is intentionally not enabled in this build. */
class AutomationEngine(private val rules:RuleManager){fun savedRules()=rules.all()}

//songtianming

package com.tang.intellij.lua.codeInsight.template.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.psi.PsiFile
import com.tang.intellij.lua.codeInsight.template.context.LuaFunContextType
import com.tang.intellij.lua.psi.*

class LuaCurrentClassNameMacro : Macro() {
    override fun getPresentableName() = "LuaCurrentClassName()"

    override fun getName() = "LuaCurrentClassName"

    override fun calculateResult(expressions: Array<out Expression>, context: ExpressionContext?): Result? {
        var e = context?.psiElementAtStartOffset
        while (e != null && e !is PsiFile) {
            e = e.parent
            when (e) {
                is LuaClassMethodDef -> {
                    var name: String = e.classMethodName.text ?: ""
                    if (name.contains("."))
                        name = name.split(".")[0]
                    else if (name.contains(":"))
                        name = name.split(":")[0]
                    return TextResult(name)
                }
            }
        }
        return null
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext?): Array<LookupElement>? {
        var e = context?.psiElementAtStartOffset
        val list = mutableListOf<LookupElement>()
        while (e != null && e !is PsiFile) {
            e = e.parent
            when (e) {
                is LuaClassMethodDef -> list.add(LookupElementBuilder.create(e.classMethodName.text))
            }
        }
        return list.toTypedArray()
    }

    override fun isAcceptableInContext(context: TemplateContextType): Boolean {
        return context is LuaFunContextType
    }
}
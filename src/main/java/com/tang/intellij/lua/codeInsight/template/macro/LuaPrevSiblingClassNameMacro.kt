//songtianming

package com.tang.intellij.lua.codeInsight.template.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.codeInsight.template.context.LuaFunContextType
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaLocalDefImpl

class LuaPrevSiblingClassNameMacro : Macro() {
    override fun getPresentableName() = "LuaPrevSiblingClassName()"

    override fun getName() = "LuaPrevSiblingClassName"

    override fun calculateResult(expressions: Array<out Expression>, context: ExpressionContext?): Result? {
        var e = context?.psiElementAtStartOffset
        while (e != null && e !is PsiFile) {
            e = e.prevSibling
            if (e is LuaLocalDefImpl)
            {
                val cls = PsiTreeUtil.findChildOfType(e, LuaDocTagClass::class.java)
                if (cls != null)
                {
                    return TextResult(e.nameList?.text ?: "")
//                    return TextResult(cls.name)
                }
            }
            if (e is LuaClassMethodDef)
            {
                var name: String = e.classMethodName.text ?: ""
                if (name.contains("."))
                    name = name.split(".")[0]
                else if (name.contains(":"))
                    name = name.split(":")[0]
                return TextResult(name)
            }
        }
        return null
    }

//    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext?): Array<LookupElement>? {
//        var e = context?.psiElementAtStartOffset
//        val list = mutableListOf<LookupElement>()
//        while (e != null && e !is PsiFile) {
//            e = e.prevSibling
//            when (e) {
//                is LuaLocalDefImpl -> list.add(LookupElementBuilder.create(e.name ?: ""))
//            }
//        }
//        return list.toTypedArray()
//    }

//    override fun isAcceptableInContext(context: TemplateContextType): Boolean {
//        return context is LuaFunContextType
//    }
}
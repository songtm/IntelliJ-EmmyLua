/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaCallExprImpl
import com.tang.intellij.lua.psi.impl.LuaIndexExprImpl
import com.tang.intellij.lua.psi.impl.LuaListArgsImpl
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

data class ParameterInfoType(val sig: IFunSignature, val isColonStyle: Boolean)

/**
 *
 * Created by tangzx on 2016/12/25.
 */
class LuaParameterInfoHandler : ParameterInfoHandler<LuaArgs, ParameterInfoType> {
    override fun couldShowInLookup(): Boolean {
        return false
    }

    override fun getParametersForLookup(lookupElement: LookupElement, parameterInfoContext: ParameterInfoContext): Array<Any>? {
        return emptyArray()
    }

    override fun getParametersForDocumentation(o: ParameterInfoType, parameterInfoContext: ParameterInfoContext): Array<Any>? {
        return emptyArray()
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): LuaArgs? {
        val file = context.file
        val luaArgs = PsiTreeUtil.findElementOfClassAtOffset(file, context.offset, LuaArgs::class.java, false)
        if (luaArgs != null) {
            val callExpr = luaArgs.parent as LuaCallExpr
            var isColonStyle = callExpr.isMethodColonCall
            //songtm ctor不显示self参数提示
            if ((callExpr.isMethodColonCall || callExpr.isMethodDotCall) && callExpr.firstChild is LuaIndexExprImpl)
            {
                val memName = (callExpr.firstChild as LuaIndexExprImpl).id?.text
                if (memName == "ctor" || memName == "new")
                    isColonStyle = true
            }
            //
            val type = callExpr.guessParentType(SearchContext.get(context.project))
            val list = mutableListOf<ParameterInfoType>()
            TyUnion.each(type) { ty ->
                if (ty is ITyFunction) {
                    ty.process(Processor {
                        if ((it.colonCall && !isColonStyle) || it.params.isNotEmpty() || it.hasVarargs()) {
                            list.add(ParameterInfoType(it, isColonStyle))
                        }
                        true
                    })
                }
            }
            context.itemsToShow = list.toTypedArray()
        }
        return luaArgs
    }

    override fun showParameterInfo(args: LuaArgs, context: CreateParameterInfoContext) {
        context.showHint(args, args.textRange.startOffset + 1, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): LuaArgs? {
        val file = context.file
        return PsiTreeUtil.findElementOfClassAtOffset(file, context.offset, LuaArgs::class.java, false)
    }

    //songtm temp fix 类函数内调用函数时参数空格后会消失不见, ParameterInfoController.executeUpdateParameterInfo: parameterOwner.equals(elementForUpdating)不相等
    override fun processFoundElementForUpdatingParameterInfo(parameterOwner: LuaArgs?, context: UpdateParameterInfoContext) {
        context.parameterOwner = parameterOwner
    }
    override fun updateParameterInfo(args: LuaArgs, context: UpdateParameterInfoContext) {
        if (args is LuaListArgs) {
            val index = ParameterInfoUtils.getCurrentParameterIndex(args.node, context.offset, LuaTypes.COMMA)
            context.setCurrentParameter(index)
        }
    }

    override fun getParameterCloseChars(): String? {
        return ",()"
    }

    override fun tracksParameterIndex(): Boolean {
        return true
    }

    override fun updateUI(o: ParameterInfoType?, context: ParameterInfoUIContext) {
        if (o == null)
            return

        //songtm 2020年10月8日, 根据第一个参数的类型找到vargs的类型 添加到参数提示后面!
        o.sig.appendVargsMember = null
        var exprList = (context.parameterOwner as LuaListArgsImpl).exprList
        if (exprList != null && exprList.size > 0 && context.parameterOwner.parent is LuaCallExprImpl)
        {
            val searchContext = SearchContext.get(context.parameterOwner.project)
            val callExp = context.parameterOwner.parent as LuaCallExprImpl
            var shouldAdd = false
            var mfuncName = ""
            if (callExp.isFunctionCall)
            {
                mfuncName = callExp.firstChild.text//全局函数
                shouldAdd = appendVargsmap.containsKey(mfuncName)
            }
            else if (callExp.isMethodColonCall || callExp.isMethodDotCall)
            {
                val unionClsName = (callExp.firstChild as LuaIndexExprImpl).guessParentType(searchContext).toString()
                val memName: String? = (callExp.firstChild as LuaIndexExprImpl).id?.text
                var clsnames = unionClsName.split("|")

                for (eachClsName in clsnames) {
                    if (appendVargsmap.containsKey("$eachClsName:$memName"))
                    {
                        mfuncName = "$eachClsName:$memName"
                        break
                    }
                }
                shouldAdd = appendVargsmap.containsKey(mfuncName)
            }
            if (shouldAdd)
            {
                val typeParamIndex = appendVargsmap[mfuncName]!!.first
                if (typeParamIndex < exprList.size) {
                    var luaExpr: LuaExpr = exprList[typeParamIndex]
                    var guessType: ITy = luaExpr.guessType(searchContext)
                    if (guessType is TyClass) {
                        var findMember: LuaClassMember? = guessType.findMember(appendVargsmap[mfuncName]!!.second, searchContext)
                        if (findMember != null && findMember is LuaClassMethodDef) {
                            o.sig.appendVargsMember = findMember
                        }
                    }
                }
            }
        }
        //

        val index = context.currentParameterIndex
        var start = 0
        var end = 0
        val str = buildString {
            o.sig.processArgs(null, o.isColonStyle, true) { idx, pi ->
                if (idx > 0) append(", ")
                if (idx == index) start = length
                if (index > idx && pi.name == "...") start = length
                append(pi.name)
                append(":")
                append(pi.ty.displayName)
                if (idx == index) end = length
                if (index > idx && pi.name == "...") end = length
                true
            }
            if (o.sig.hasVarargs()) {

            }
        }
        if (str.isNotEmpty()) {
            context.setupUIComponentPresentation(
                    str,
                    start,
                    end,
                    false,
                    false,
                    false,
                    context.defaultParameterColor
            )
        }
    }

    companion object
    {
        var appendVargsmap:MutableMap<String, Pair<Int, String> > =  emptyMap<String, Pair<Int, String>>().toMutableMap()
        init {
            val str = LuaSettings.instance.appendVargs //"UIManager:push|1|init;pushUI|1|init"
            var split = str.split(";")
            for (s in split) {
                var split1 = s.split("|")
                if (split1.size == 3)
                    appendVargsmap[split1[0]] = Pair(split1[1].toInt(),  split1[2])
            }
        }
    }

}

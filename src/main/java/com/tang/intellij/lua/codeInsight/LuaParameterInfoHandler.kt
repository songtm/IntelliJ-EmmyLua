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
            val isColonStyle = callExpr.isMethodColonCall
            val type = callExpr.guessParentType(SearchContext.get(context.project))
            val list = mutableListOf<ParameterInfoType>()
            TyUnion.each(type) { ty ->
                if (ty is ITyFunction) {
                    ty.process(Processor {
                        if ((it.colonCall && !isColonStyle) || it.params.isNotEmpty()) {
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
            var clsName = ""
            if (callExp.isFunctionCall)
            {
                clsName = callExp.firstChild.text//全局函数
                shouldAdd = appendVargsmap.containsKey(clsName)
            }
            else if (callExp.isMethodColonCall || callExp.isMethodDotCall)
            {
                clsName = (callExp.firstChild as LuaIndexExprImpl).guessParentType(searchContext).toString()
                val memName: String? = (callExp.firstChild as LuaIndexExprImpl).id?.text
                shouldAdd = (appendVargsmap.containsKey(clsName) && memName != null && appendVargsmap[clsName]!!.first == memName)
            }
            if (shouldAdd)
            {
                var luaExpr: LuaExpr = exprList[0]
                var guessType: ITy = luaExpr.guessType(searchContext)
                if (guessType is TyClass)
                {
                    var findMember: LuaClassMember? = guessType.findMember(appendVargsmap[clsName]!!.second, searchContext)
                    if (findMember != null && findMember is LuaClassMethodDef)
                    {
                        o.sig.appendVargsMember = findMember
                    }
                }
            }
        }
        //

        val index = context.currentParameterIndex
        var start = 0
        var end = 0
        val str = buildString {
            o.sig.processArgs(null, o.isColonStyle) { idx, pi ->
                if (idx > 0) append(", ")
                if (idx == index) start = length
                append(pi.name)
                append(":")
                append(pi.ty.displayName)
                if (idx == index) end = length
                true
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
        var appendVargsmap:MutableMap<String, Pair<String, String>> =  emptyMap<String, Pair<String, String>>().toMutableMap()
        init {
            val str = "UIManager|push|init;pushUI|init"
            var split = str.split(";")
            for (s in split) {
                var split1 = s.split("|")
                if (split1.size == 2)
                    appendVargsmap[split1[0]] = Pair("songtmhahaha", split1[1])
                if (split1.size == 3)
                    appendVargsmap[split1[0]] = Pair(split1[1], split1[2])
            }
        }
    }

}

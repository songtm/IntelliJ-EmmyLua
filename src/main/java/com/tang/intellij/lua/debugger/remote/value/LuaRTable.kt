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

package com.tang.intellij.lua.debugger.remote.value

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import com.tang.intellij.lua.debugger.remote.LuaMobDebugProcess
import org.luaj.vm2.LuaValue
import java.util.*

/**
 *
 * Created by tangzx on 2017/4/16.
 */
class LuaRTable(name: String) : LuaRValue(name) {
    private var list: XValueChildrenList? = null
    private val desc = "table"
    private val unityComName = "components[]"
    private val usertypeName = "__utype__"
    public var data: LuaValue? = null
    private var userdataType: LuaValue? = null
    private var empty: Boolean = false

    override fun parse(data: LuaValue, desc: String) {
        userdataType = data.get(usertypeName)
        empty = data.get("__tbempty__") != LuaValue.NIL
        this.data = data
    }

    override fun computePresentation(xValueNode: XValueNode, xValuePlace: XValuePlace) {
        when {
            userdataType != LuaValue.NIL -> {
                val luatype = userdataType.toString()
                val lastIndexOf = luatype.lastIndexOf('(')
                var type = luatype
                var des = ""
                if (lastIndexOf >= 0) {
                    type = luatype.substring(lastIndexOf + 1, luatype.length - 1)
                    des = luatype.substring(0, lastIndexOf)
                }
                xValueNode.setPresentation(AllIcons.Nodes.ExceptionClass, type, des, !empty)
            }
            name == unityComName -> xValueNode.setPresentation(AllIcons.Nodes.Class, "UnityEngine.Component", "", !empty)
            else -> xValueNode.setPresentation(AllIcons.Json.Object, "table", desc, !empty)
        }
    }

    private val evalExpr: String
        get() {
            var name = name
            val properties = ArrayList<String>()
            var parent = this.parent
            while (parent != null) {
                val parentName = parent.name
                properties.add(name)
                name = parentName
                parent = parent.parent
            }

            return buildString {
                append(name)
                for (i in properties.indices.reversed()) {
                    val parentName = properties[i]
                    when {
                        parentName == unityComName -> append(":GetComponents(typeof(UnityEngine.Component)):ToTable()")
                        parentName.startsWith("[") -> append(parentName)
                        parentName.matches("[0-9]+".toRegex()) -> append("[$parentName]")
                        else -> append(String.format("[\"%s\"]", parentName))
                    }
                }
            }
        }

    override fun computeChildren(node: XCompositeNode) {
        if (list == null) {
            val process = session.debugProcess as LuaMobDebugProcess
            process.evaluator?.evaluate(evalExpr, object : XDebuggerEvaluator.XEvaluationCallback {
                override fun errorOccurred(err: String) {
                    node.setErrorMessage(err)
                }

                override fun evaluated(tableValue: XValue) {
//                    //////////tmp solution,非栈顶帧处理
//                    var tableValue = tableValue
//                    if (data != null && !(process.session as XDebugSessionImpl).isTopFrameSelected)
//                        tableValue = LuaRValue.create(myName, data as LuaValue, myName, process.session)
//                    //////////

                    val list = XValueChildrenList()
                    val tbl = tableValue as? LuaRTable ?: return
                    val table = tbl.data?.checktable()
                    if (table != null) {
                        var scoreMap: MutableMap<LuaValue, Int> = HashMap()
                        val keys = table.keys()
//                        keys.forEachIndexed { index, luaValue ->
//                            var score = 0
//                            val tabval = table.get(luaValue)
//                            val valtype = tabval.typename()
//                            val istab = valtype == "table"
//                            score += when {
//                                luaValue.toString() == unityComName -> 24000
//                                istab && tabval.get(usertypeName) != LuaValue.NIL -> 12000
//                                istab -> 6000
//                                valtype == "string" -> 3000
//                                valtype == "boolean" -> 2000
//                                valtype == "number" -> 1000
//                                else -> 0
//                            }
//                            score += -index //原始顺序
//                            scoreMap[luaValue] = -score
//                        }
//                        keys.sortBy { scoreMap[it] }

                        keys.sortWith(compareBy(
                                { if (it.isnumber()) it.toint() else 0  },
                                { if (it.isstring()) it.toString() else ""},
                                { if (it.isboolean()) it.toboolean() else false}
                        ))

                        for (key in keys) {
                            if (key.toString() == usertypeName) continue
                            val value = LuaRValue.create(key.toString(), table.get(key), "", session)
                            value.parent = this@LuaRTable
                            list.add(value)
                        }
                    }
                    node.addChildren(list, true)
                    this@LuaRTable.list = list
                }
            }, null)
        } else
            node.addChildren(list!!, true)
    }
}
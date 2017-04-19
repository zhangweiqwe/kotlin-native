/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.backend.konan.util

class File(val path:String) {
    private val javaFile = java.io.File(path)
    val absoluteFile: File get() = File(javaFile.absolutePath)
    val name: String get() = javaFile.name
    val parent: String get() = javaFile.parent
    val absolutePath: String get() = javaFile.absolutePath
    fun exists(): Boolean = javaFile.exists()
    fun deleteOnExit() = javaFile.deleteOnExit()
    companion object {
        fun createTempFile(prefix: String, suffix: String) = File(java.io.File.createTempFile(prefix, suffix).absolutePath)
    }

}
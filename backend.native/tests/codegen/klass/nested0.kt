class A {
    init {
        println("A::<init>")
    }
    class ACin0 {
        var innerAAICin0Index = 0
        init {
            println("A::ACin0::<init>")
        }
        class AACin0{
            init {
                println("A::ACin0::AACin0::<init>")
            }
        }
        inner class AAICin0{
            init {
                innerAAICin0Index++
                println("A::ACin0::AAICin0::<init>")
            }
        }
    }
    class ACin1{
        init {
            println("A::ACin1::<init>")
        }
    }
    object AOin0 {
        init {
            println("A::AOin0::<init>")
        }

    }
    object A0in1 {
        init {
            println("A::AOin0::<init>")
        }
    }

    val a = object {
        init {
            println("A::$1::<init>")
        }
    }
}

fun main(arg:Array<String>) {
    A()
    A.ACin0().AAICin0()
}

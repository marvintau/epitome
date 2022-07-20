package epitome.mem.sram

import chisel3._
import chisel3.util._
import chiselFv._


// tobe moved to util packages later.

object HoldUnless {
  def apply[T <: Data](x: T, en: Bool): T = Mux(en, x, RegEnable(x, 0.U.asTypeOf(x), en))
}

object ReadAndHold {
  def apply[T <: Data](x: Mem[T], addr: UInt, en: Bool): T = HoldUnless(x.read(addr), en)
  def apply[T <: Data](x: SyncReadMem[T], addr: UInt, en: Bool): T = HoldUnless(x.read(addr, en), RegNext(en))
}


class A(val sets: Int) extends Bundle {

  val addr = Output(UInt(log2Up(sets).W))

  def apply(addr: UInt) = {
    this.addr := addr
    this
  }

}

class AW[T <: Data](private val t: T, sets: Int, val ways: Int = 1) extends A(sets) {

  val data = Output(t)
  val waymask = if (ways > 1) Some(Output(UInt(ways.W))) else None

  def apply(data: T, addr: UInt, waymask: UInt) : Unit = {
    super.apply(addr)
    this.data := data
    this.waymask.map(_ := waymask)
    this
  }

}

class R[T <: Data](private val t: T, val ways: Int = 1) extends Bundle {
  val data = Output(Vec(ways, t))
}

class BusR[T <: Data](private val t: T, sets: Int, val ways: Int = 1) extends Bundle {

  val req = Decoupled(new A(sets))
  val res = Flipped(new R(t, ways))

  def apply(valid: Bool, index: UInt) : Unit = {
    this.req.bits.apply(index)
    this.req.valid := valid
    this
  }
}

class BusW[T <: Data](private val t: T, sets: Int, val ways: Int = 1) extends Bundle {

  val req = Decoupled(new AW(t, sets, ways))

  def apply(valid: Bool, data: T, addr: UInt, waymask: UInt) : Unit = {
    this.req.bits.apply(data = data, addr = addr, waymask = waymask)
    this.req.valid := valid
    this
  }
}

class Simplex[T <: Data](t: T, sets: Int, ways: Int = 1) extends Module {
  
  val io = IO(new Bundle{
    val r = Flipped(new BusR(t, sets, ways))
    val w = Flipped(new BusW(t, sets, ways))
		val onReset = Output(Bool())
  })

  val wordType = UInt(t.getWidth.W)
  val mem = SyncReadMem(sets, Vec(ways, wordType))

  // resetting logics
  val onReset = RegInit(true.B)
  val (resetAddr, resetDone) = Counter(onReset, sets)
  when (resetDone) {
    onReset := false.B
  }

  val enableW = io.w.req.valid || onReset
  val enableR = !enableW && io.r.req.valid

  val waymask = Mux(onReset, Fill(ways, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))

  val addrW = Mux(onReset, resetAddr, io.w.req.bits.addr)
  val wordW = Mux(onReset, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val dataW = VecInit(Seq.fill(ways)(wordW))

  when (enableW) {
    mem.write(addrW, dataW, waymask.asBools)
  }
  
  val rdata = ReadAndHold(mem, io.r.req.bits.addr, enableR).map(_.asTypeOf(t))

  io.r.res.data := VecInit(rdata)

  io.r.req.ready := !enableW
  io.w.req.ready := true.B

	io.onReset := onReset
}

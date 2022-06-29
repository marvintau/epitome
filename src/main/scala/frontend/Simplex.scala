package nutshell.epitome.mem.sram

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

// NOTE: Naming Convention
// =======================
//
// Y.M.T. 6/25/2022
//
// For better understanding and typographic viewing, the code here (and anywhere else) follows
// the following conventions:
//
// 1. Follow the existing convention:
//
//    For example, the A, AW and R are for Addressing, Addressing/Writing and Reading,
//    following AXI4 naming convention. They are primitive IO bundles for building serial
//    protocol of accessing SyncReadMem.
//
// 2. Regarding number:
//
//    Often we need to specify the amount/capacity/size of a buffer/cache/register bank with a
//    number (typically integer). 
//
//    - When we need to specify the capacity in __bit/byte__, we call it __"size"__.
//
//    - When we need to specify the amount of __entries/records__, we call it __"sets"__.
//
//    Other English words are discouraged, including:
//
//    - "num*/nr*/n*", indicating "number of something", are verbose.
//
//    - "set", borrowed from cache concepts, is ambiguous and not meaning to a number.
//
//    - "total/count/amount/capacity/volume" are verbose, and some of which are ambiguous.
//
// 3. Regarding n-th item:
//
//    Often we need to refer to some specific item/entry/register/module among a set of them,
//
//    - When we are building a memory device and referring a position in number form, we call
//      it __"addr"__.
//
//    - otherwise, __"index"__.
//
//    Other English words and other abbreviations are discouraged, including:
//
//    - "id/idx", we have sufficient storage for code.
//
//    - "elem/element/item/entry", ambiguous. It can be referred as either the index (number)
//      of item or the item itself.
//
// 4. Other rules: 
//
//    - Do not use abbreviations unless the name is too long and verbose. If have to, use
//      prefix-like abbreviations (abbrev.), not use acronym (idx, rst, clk). 
//			
//    - Do not use uncommon abbreviations/acronym, unless it appears frequently in the code
//      base. If have to use, make it well explained and documented.
//
//			common or conventional abbreviations:
//			- req/res - request/response
//			- prev/next/curr - previous/next/current
//			- R/W(suffix) - read/write
//      - I/O(suffix) - input/output
//
//      for example, the AXI4 Bus uses A/AW/R for Address, Address & Write, and Read. It is
//      better to integrate them into your bus implementation.
//
//    - If lot of names share same prefix, consider create a new namespace, which can be either
//      package/class or other encapsuling method, and remove the prefix.
//
// 5. Advices:
//
//    - When you feel not sure about name, please check out www.thesaurus.com. 


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

class Simplex[T <: Data](t: T, sets: Int, ways: Int = 1) extends Module with Formal{
  
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

  // Formal Verifications
  // ====================
  
  assert(!(enableW && enableR))

  val Seq(prevAddrW1,   prevAddrW2)   = ShiftRegisters(addrW, 2)
  val Seq(prevReset1,   prevReset2)   = ShiftRegisters(onReset, 2, false.B, true.B)
  val Seq(prevEnableW1, prevEnableW2) = ShiftRegisters(enableW, 2, false.B, true.B)

  val prevRead = Wire(Vec(ways, wordType))
  prevRead := mem(prevAddrW1)

	// simply assert that
  when (onReset && prevReset1) {
    assert(addrW === 1.U + prevAddrW1)
  }

  // during onReset, prevAddrW2 is equal to 2-cycle-delayed resetAddr
  when (onReset && prevReset2) {

    assert(prevAddrW2 + 2.U === resetAddr) 
    assert(prevRead.asUInt() === dataW.asUInt())

  }

  // when (!onReset && prevEnableW2) {
  //   assert(prevRead.asUInt() === dataW.asUInt())
  // }
}

import chisel3.stage.ChiselStage
object Simplex extends App {

  Check.kInduction(() => new Simplex(UInt(8.W), 4))
  Check.bmc(() => new Simplex(UInt(8.W), 4))
	
  //(new ChiselStage).emitVerilog(new Simplex(UInt(8.W), 8))
}

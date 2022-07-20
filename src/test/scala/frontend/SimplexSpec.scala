
package nutshell.epitome.mem.sram

import scala.util._

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

import chisel3._
import chisel3.experimental.BundleLiterals._

import chiseltest._
import chiseltest.formal._
import chiseltest.experimental._


class FormalSimplexSpec(val width: Int, val size: Int) extends Module {
  
  val dut = Module(new Simplex(UInt(width.W), size))

  val io = IO(chiselTypeOf(dut.io))
  io <> dut.io

  val enableW = observe(dut.enableW)
  val enableR = observe(dut.enableR)
  val onReset = observe(dut.onReset)
  val resetAddr = observe(dut.resetAddr)
  val resetDone = observe(dut.resetDone)

  val addrW = observe(dut.addrW)
  val wordW = observe(dut.wordW)

  assert(!(enableW && enableR))

  // check resetting stage
  when(onReset) {
    assert(!enableR)

    when(past(onReset, 1)) {
      assert(!stable(resetAddr))
      assert(resetAddr === past(resetAddr, 1) + 1.U)
    }

  // check working stage
  } .elsewhen (!past(onReset, 1)) {
    assert(stable(resetAddr))

    val ioAddrR = io.r.req.bits.addr
    val ioAddrW = io.w.req.bits.addr
    val ioDataR = io.r.res.data.asUInt()
    val ioDataW = io.w.req.bits.data

    val ioReqR  = io.r.req.valid
    val ioReqW  = io.w.req.valid

    // check writing
    when(ioReqW) {
      assert(enableW)
      assert(addrW === ioAddrW)
      assert(wordW === ioDataW)

      // when writing enabled, reading request is suppressed
      when (ioReqR){
        assert(!enableR)
      }
    
    // when writing not valid anymore, and reading become valid
    // preferably if we may specify if a signal is risen / fallen n-cycles ago
    // or before/after some other signal event/pattern
    
    } .elsewhen (past(ioReqW, 3) && past(ioReqW, 2) && past(!ioReqW, 1) && !ioReqW && stable(ioReqR) && ioReqR) {
      assert(past(enableW, 2) && !past(enableW, 1) && !enableW)
      assert(past(!enableR, 3) && past(enableR, 1) && enableR)

      // when we are trying to read the addr we just wrote in last cycle,
      // and the read request is still valid in current cycle

      when (ioDataW === "hBEEF".U && stable(ioAddrW) && stable(ioAddrR) && ioAddrW === ioAddrR) {
        assert(ioDataR === "hBEEF".U)
      }
    } 
  }
}

class SimplexSpec extends AnyFlatSpec with ChiselScalatestTester with Formal with FormalBackendOption {

  "Simulated" should "work for simulation" in {
    
    val width = 64
    val size = 16

    test(new Simplex(UInt(width.W), size)) { dut =>

      // onReset should remain true within the first <size> cycles

      for (index <- 0 until size) {
        dut.io.onReset.expect(true.B)
        dut.io.r.req.ready.expect(false.B)
        dut.clock.step()
      }
      dut.io.onReset.expect(false.B)
      dut.io.r.req.ready.expect(true.B)

      // check each of memory unit is 0 (initialized) 
      //
      // however, this clause is weak since if Chisel initializes all
      // memory with 0. And it does not guarantee that you are reading
      // the data at the right cycle.

      dut.io.w.req.valid.poke(false.B)
      dut.io.r.req.valid.poke(true.B)
      for (addr <- 0 until size) {
        dut.clock.step()
        dut.io.r.req.bits.addr.poke(addr.U)
        dut.clock.step()
        dut.io.r.res.data.asUInt().expect(0.U)
      }

      // then place flag for writing into memory.

      val rand = new Random()

      for (_ <- 0 until 20) {
        
        val data = rand.nextInt(1 << width)
        val addr = rand.nextInt(size)

        // first write
        dut.clock.step()
        dut.io.w.req.valid.poke(true.B)
        dut.io.r.req.valid.poke(false.B)

        dut.io.w.req.bits.data.poke(data.U)
        dut.io.w.req.bits.addr.poke(addr.U)
        dut.clock.step()

        dut.io.w.req.valid.poke(false.B)
        dut.io.r.req.valid.poke(true.B)
        dut.io.r.req.bits.addr.poke(addr.U)
        
        dut.clock.step()
        dut.io.r.res.data.asUInt().expect(data.U)

      }
    }

    verify(new FormalSimplexSpec(width, size), Seq(BoundedCheck(20), DefaultBackend))
  }
}

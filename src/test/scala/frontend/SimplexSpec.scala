
package nutshell.epitome.mem.sram

import scala.util._

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

import chisel3._
import chiseltest._

import chisel3.experimental.BundleLiterals._

class SimplexSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SimplexMem"

  it should "work as expected" in {
    
    val width = 8
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
        printf("%x\n", dut.io.r.res.data.asUInt().peek())
        dut.io.r.res.data.asUInt().expect(data.U)

      }
    }

  }
}

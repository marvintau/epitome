package nutshell.epitome.branch

import scala.util._

import org.scalatest._

import chisel3._
import chisel3.util._

import chiseltest._
import chisel3.experimental.BundleLiterals._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DUT(val idxLen: Int) extends Module {
  val io = IO(new Bundle {
    val entry = Input(new EntryAddr(idxLen))
  })
}

class EntryAddrSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "EntryAddrSpec"

  it should "get the components" in {

    val entryCount = 512
    val idxLen = log2Up(entryCount >> 2)

    test(new DUT(idxLen)) { dut =>

      val rand = new Random()
      val nextIdx = rand.nextInt(1 << idxLen).U

      dut.io.entry.getIdx(nextIdx).asUInt().expect((nextIdx >> 2) & (1 << idxLen - 1).U)
    }

  }

}

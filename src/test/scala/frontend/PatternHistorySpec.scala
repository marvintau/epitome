/* package nutshell.epitome.mem.frontend */
//
// import scala.util._
//
// import org.scalatest._
// import org.scalatest.matchers.should.Matchers
// import org.scalatest.flatspec.AnyFlatSpec
//
// import chisel3._
// import chisel3.experimental.BundleLiterals._
//
// import chiseltest._
// import chiseltest.formal._
// import chiseltest.experimental._
//
// class FormalPatternHistorySpec(val width: Int, val size: Int) extends Module {
//
//   val dut = Module(new (UInt(width.W), size))
//
//   val io = IO(chiselTypeOf(dut.io))
//   io <> dut.io
//
//   val enableW = observe(dut.enableW)
//   val enableR = observe(dut.enableR)
//   val onReset = observe(dut.onReset)
//   val resetAddr = observe(dut.resetAddr)
//   val resetDone = observe(dut.resetDone)
//
//   val addrW = observe(dut.addrW)
//   val wordW = observe(dut.wordW)
//
//   assert(!(enableW && enableR))
//
//   // check resetting stage
//   when(onReset) {
//     assert(!enableR)
//
//     when(past(onReset, 1)) {
//       assert(!stable(resetAddr))
//       assert(resetAddr === past(resetAddr, 1) + 1.U)
//     }
//
//   // check working stage
//   } .elsewhen (!past(onReset, 1)) {
//     assert(stable(resetAddr))
//
//     val ioAddrR = io.r.req.bits.addr
//     val ioAddrW = io.w.req.bits.addr
//     val ioDataR = io.r.res.data.asUInt()
//     val ioDataW = io.w.req.bits.data
//
//     val ioReqR  = io.r.req.valid
//     val ioReqW  = io.w.req.valid
//
//     // check writing
//     when(ioReqW) {
//       assert(enableW)
//       assert(addrW === ioAddrW)
//       assert(wordW === ioDataW)
//
//       // when writing enabled, reading request is suppressed
//       when (ioReqR){
//         assert(!enableR)
//       }
//
//     // when writing not valid anymore, and reading become valid
//     // preferably if we may specify if a signal is risen / fallen n-cycles ago
//     // or before/after some other signal event/pattern
//
//     } .elsewhen (past(ioReqW, 3) && past(ioReqW, 2) && past(!ioReqW, 1) && !ioReqW && stable(ioReqR) && ioReqR) {
//       assert(past(enableW, 2) && !past(enableW, 1) && !enableW)
//       assert(past(!enableR, 3) && past(enableR, 1) && enableR)
//
//       // when we are trying to read the addr we just wrote in last cycle,
//       // and the read request is still valid in current cycle
//
//       when (ioDataW === "hBEEF".U && stable(ioAddrW) && stable(ioAddrR) && ioAddrW === ioAddrR) {
//         assert(ioDataR === "hBEEF".U)
//       }
//     }
//   }
// }
//
//
// class PatternHistorySpec extends AnyFlatSpec with ChiselScalatestTester with Formal with FormalBackendOption {
//
//   "Simulated" should "work for simulation" in {
//
//     val width = 64
//     val size = 16
//
//     verify(new FormalSimplexSpec(width, size), Seq(BoundedCheck(20), DefaultBackend))
//   }
/* } */

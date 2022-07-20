package epitome.branch

import chisel3._
import chisel3.util._

// Feedback is issued by the write-back stage. And will be used for 
// determining take the branch or flush the whole pipeline.
//
// The Feedback bundle here can be considered as a protocol between
// the write-back (WB) and the branch-prediction (BP) module. As a
// convention, we declare the protocol at the place where the data
// is accepted, instead of issued.

class Feedback extends Bundle with Config {

  val valid = Output(Bool())

  val pc          = Output(UInt(VIRT_MEM_ADDR_LEN.W))
  val missed      = Output(Bool())
  val target      = Output(UInt(VIRT_MEM_ADDR_LEN.W))
  val fuOpType    = Output(FuOpType())
  val branchType  = Output(BranchType())
  val branchTaken = Output(Bool())  // for branch
  val compressed  = Output(Bool())  // for ras, save PC+2 to stack if is RVC
}



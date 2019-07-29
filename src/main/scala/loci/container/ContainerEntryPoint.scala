package loci.container

import loci.Peer

import scala.annotation.meta.{getter, setter}

@getter @setter
trait ContainerEntryPoint {
  protected val containerPort : Integer = 0
  protected val containerPeer : Peer
}

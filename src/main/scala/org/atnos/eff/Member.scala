package org.atnos.eff

import Effects._
import scalaz._

/**
 * Member typeclass for effects belonging to a stack of effects R
 *
 * If T is a member of R then we can:
 *
 * - create a Union of effects from a single effect with "inject"
 * - extract an effect value from a union if there is such an effect in the stack
 */
trait Member[T[_], R] {
  type Out <: Effects

  def inject[V](tv: T[V]): Union[R, V]

  def project[V](u: Union[R, V]): Union[Out, V] \/ T[V]
}

object Member extends MemberImplicits {

  def apply[T[_], R](implicit m: Member[T, R]): Member[T, R] =
    m

  def aux[T[_], R, U](implicit m: Member.Aux[T, R, U]): Member.Aux[T, R, U] =
    m

  type Aux[T[_], R, U] = Member[T, R] { type Out = U }

  def ZeroMember[T[_], R <: Effects]: Member.Aux[T, T |: R, R] = new Member[T, T |: R] {
    type Out = R

    def inject[V](effect: T[V]): Union[T |: R, V] =
      Union.now(effect)

    def project[V](union: Union[T |: R, V]): Union[R, V] \/ T[V] =
      union match {
        case UnionNow(x) => \/-(x)
        case UnionNext(u@UnionNow(x)) => -\/(UnionNow(x).asInstanceOf[Union[R, V]])
        case UnionNext(u@UnionNext(x)) => -\/(UnionNext(x).asInstanceOf[Union[R, V]])
      }
  }

  def SuccessorMember[T[_], O[_], R <: Effects, U <: Effects](implicit m: Member.Aux[T, R, U]): Member.Aux[T, O |: R, O |: U] = new Member[T, O |: R] {
    type Out = O |: U

    def inject[V](effect: T[V]) =
      Union.next(m.inject[V](effect))

    def project[V](union: Union[O |: R, V]): Union[Out, V] \/ T[V] =
      union match {
        case UnionNow(x) => -\/(UnionNow(x).asInstanceOf[Union[Out, V]])
        case UnionNext(u) => m.project[V](u).leftMap(u1 => UnionNext(u1).asInstanceOf[Union[Out, V]])
      }
  }

  /**
   * helper method to untag a tagged effect
   */
  def untagMember[T[_], R, TT](m: Member[({type X[A]=T[A] @@ TT})#X, R]): Member.Aux[T, R, m.Out] =
    new Member[T, R] {
      type Out = m.Out

      def inject[V](tv: T[V]): Union[R, V] =
        m.inject(Tag(tv))

      def project[V](u: Union[R, V]): Union[Out, V] \/ T[V] =
        m.project(u).map(Tag.unwrap)
    }

  type <=[M[_], R] = Member[M, R]
}

trait MemberImplicits extends MemberImplicits1 {
  implicit def zero[T[_]]: Member.Aux[T, T |: NoEffect, NoEffect] =
    Member.ZeroMember[T, NoEffect]
}

trait MemberImplicits1 extends MemberImplicits2 {
  implicit def first[T[_], R <: Effects]: Member.Aux[T, T |: R, R] =
    Member.ZeroMember[T, R]
}

trait MemberImplicits2 {
  implicit def successor[T[_], O[_], R <: Effects, U <: Effects](implicit m: Member.Aux[T, R, U]): Member.Aux[T, O |: R, O |: U] =
    Member.SuccessorMember[T, O, R, U](m)
}


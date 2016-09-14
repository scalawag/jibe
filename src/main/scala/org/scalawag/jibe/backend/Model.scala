package org.scalawag.jibe.backend

// Needs to be strings unless we always make sure that the user has ensured a group exists.  Otherwise, we can't
// guarantee that there will be an object to put here.

case class UserGroupAssoc(user: User, group: Group)

//case class File(path: String,
//                owner: Option[User] = None,
//                group: Option[Group] = None,
//                mode: Option[Int] = None,
//                content: Option[String] = None)

//case class System(users: List[User] = Nil,
//                  groups: List[Group] = Nil,
//                  ugassocs: List[UserGroupAssoc] = Nil,
//                  files: List[File] = Nil)

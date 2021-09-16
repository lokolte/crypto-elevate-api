package io.cryptoelevate.http.routes

object StringValidator {
  private val EmailRegex =
    """(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f"""
      .+(
        """\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:"""
      )
      .+(
        """[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|"""
      )
      .+(
        """[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])"""
      )
      .r

  private def lengthMoreOrEqualThan(str: String, maximumSize: Int): Boolean =
    str.length >= maximumSize

  private def containsUpperAndLowerCase(str: String): Boolean =
    str.exists(_.isUpper) && str.exists(_.isLower)

  private def containsDigit(str: String): Boolean =
    str.exists(_.isDigit)

  def isValidEmail(str: String): Boolean =
    EmailRegex.findFirstMatchIn(str).isDefined

  def isValidPassword(str: String): Boolean =
    lengthMoreOrEqualThan(str, 8) && containsDigit(str) && containsUpperAndLowerCase(str)

}

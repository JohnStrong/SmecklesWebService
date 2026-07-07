package auth

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EmailAllowListSpec extends AnyWordSpec with Matchers {

  "EmailAllowList.apply" should {

    "parse a single email" in {
      val list = EmailAllowList("alice@example.com")
      list.emails shouldBe Set("alice@example.com")
    }

    "parse multiple comma-delimited emails" in {
      val list = EmailAllowList("alice@example.com,bob@example.com,carol@example.com")
      list.emails shouldBe Set("alice@example.com", "bob@example.com", "carol@example.com")
    }

    "trim whitespace around emails" in {
      val list = EmailAllowList("  alice@example.com , bob@example.com  ,  carol@example.com  ")
      list.emails shouldBe Set("alice@example.com", "bob@example.com", "carol@example.com")
    }

    "normalize emails to lowercase" in {
      val list = EmailAllowList("Alice@Example.COM,BOB@TEST.COM")
      list.emails shouldBe Set("alice@example.com", "bob@test.com")
    }

    "return empty set for an empty string" in {
      val list = EmailAllowList("")
      list.emails shouldBe empty
    }

    "return empty set for whitespace-only string" in {
      val list = EmailAllowList("   ")
      list.emails shouldBe empty
    }

    "filter out empty entries from leading comma" in {
      val list = EmailAllowList(",alice@example.com")
      list.emails shouldBe Set("alice@example.com")
    }

    "filter out empty entries from trailing comma" in {
      val list = EmailAllowList("alice@example.com,")
      list.emails shouldBe Set("alice@example.com")
    }

    "filter out empty entries from consecutive commas" in {
      val list = EmailAllowList("alice@example.com,,bob@example.com")
      list.emails shouldBe Set("alice@example.com", "bob@example.com")
    }

    "filter out whitespace-only entries between commas" in {
      val list = EmailAllowList("alice@example.com,   ,bob@example.com")
      list.emails shouldBe Set("alice@example.com", "bob@example.com")
    }

    "deduplicate repeated emails" in {
      val list = EmailAllowList("alice@example.com,alice@example.com,bob@example.com")
      list.emails shouldBe Set("alice@example.com", "bob@example.com")
    }

    "deduplicate emails that differ only in case" in {
      val list = EmailAllowList("Alice@Example.com,alice@example.com")
      list.emails shouldBe Set("alice@example.com")
    }
  }

  "EmailAllowList.contains" should {

    val list = EmailAllowList("alice@example.com,bob@example.com")

    "return true for an email in the list" in {
      list.contains("alice@example.com") shouldBe true
    }

    "return true regardless of input case" in {
      list.contains("Alice@Example.COM") shouldBe true
    }

    "return true when input has surrounding whitespace" in {
      list.contains("  bob@example.com  ") shouldBe true
    }

    "return false for an email not in the list" in {
      list.contains("unknown@example.com") shouldBe false
    }

    "return false for empty string" in {
      list.contains("") shouldBe false
    }
  }
}

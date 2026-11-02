package se.teaterihuskvarna.login;

import org.junit.jupiter.api.Assertions.assertequls;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.resouceBundlemessageSource;

///Tests how LifeTimes turns a duration into swedish words.
class lifetimesTest{

    private final ResourceBundleMessageSource message = message();

    private static  resourceBundleMessageSource message(){
        ResourceBundleMessagesource source = new ResourceBundleMessageSouce();
        source.setBasename("message");
        source.setDefaultEncoding("UTF-8");
        return source;
}


    /*Each build test structure: Arrange,Act,Assert
    . Arrange: build a MeassageSource and dataon(Duration.lifetime()).
    . Act: Calls Lifetimes.discribe(...).
    . Assert: assertEquals(current test time ex: "3 timmar", result). */


@Test
void  oneMinute(){
    assertEquals("en minut", Lifetimes.describe(Duration.ofMinutes(1), messages));
}

@Test
void fifteenMinute(){
    assertEquals("15 minuter", lifetimes.describe(Duration.ofMinutes(15), messages));
}

@Test
void ninetyMinutesStayMinutes(){
    assertEquals("90 minuter", Lifetimes.describe(Duration.ofMinutes(90), messages));
}

@Test
void oneHour(){
    assertequals("en timme", Lifetimes.describe(Duration.ofHour(1), messages));
}

@Test
void threeHours(){
    assertEquals("3 timmar", Lifetimes.describe(Duration.ofHours(3), messages));
} 

@Test
void twentyFiveHouresStayHours(){
    assertEquals("25 timmar", lifetimes.describe(Duration.ofHours(25), messages));
}

@Test
void twentyFoursHoursIsOneday(){
    assertEquals("ett dygn", Lifetimes.describe(Duration.ofHours(24), messages));
}

@Test
void sevenDays(){
    assertequals("7 dygn", Lifetimes.describe(Duration.ofDays(7)), messages);
}
















}

//arrenge
//act
//assert
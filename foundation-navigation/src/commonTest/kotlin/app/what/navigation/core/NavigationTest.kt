package app.what.navigation.core

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
class TestProvider(val id: String) : NavProvider()

class TestComponent(override val data: TestProvider) : NavComponent<TestProvider> {
    @androidx.compose.runtime.Composable
    override fun content(modifier: androidx.compose.ui.Modifier): Any {
        return Unit
    }
}

class NavigationTest {

    @Test
    fun testFactoryRegistrationAndInstantiation() {
        registerScreen(TestComponent::class) { TestComponent(it) }

        val provider = TestProvider("123")
        val instance = instantiateScreen(TestComponent::class, provider)

        assertNotNull(instance)
        assertEquals("123", instance.data.id)
    }
}

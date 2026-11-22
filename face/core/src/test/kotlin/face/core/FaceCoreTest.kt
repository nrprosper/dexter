package face.core

import face.api.FaceApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FaceCoreTest {
    @Test
    fun coreSeesApi() {
        assertEquals(FaceApi.VERSION, FaceCore.API_VERSION)
    }
}

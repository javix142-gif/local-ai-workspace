package com.localai.workspace.data
import com.localai.workspace.semantic.VectorPersistence as V
import org.junit.Test
import org.junit.Assert.*
class SemanticVectorsTest {
 @Test fun exactRoundTripAndSourceInvalidation() { val v=FloatArray(768){it/768f};val hash=V.hash("approved");val row=SemanticVectorEntity("M:1","embeddinggemma:hash",memoryId="1",sourceHash=hash,dimensions=768,version=1,vector=V.encode(v),updatedAt=1);assertArrayEquals(v,V.decode(row,hash),0f);assertNull(V.decode(row,V.hash("edited")));assertNull(V.decode(row.copy(version=2),hash));assertNull(V.decode(row.copy(dimensions=256),hash)) }
 @Test fun nonNeuralWrongShapeAndInvalidNumbersRejected() { assertThrows(Exception::class.java){V.encode(FloatArray(256))};assertThrows(Exception::class.java){V.encode(FloatArray(768){Float.NaN})} }
}

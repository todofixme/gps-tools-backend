package org.devshred.gpstools.domain

import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.proto.GpsContainerMapper
import org.devshred.gpstools.domain.proto.ProtoGpsContainer
import org.devshred.gpstools.domain.proto.ProtoPoiType
import org.devshred.gpstools.domain.proto.protoGpsContainer
import org.devshred.gpstools.domain.proto.protoTrack
import org.devshred.gpstools.domain.proto.protoWayPoint
import org.junit.jupiter.api.Test

class GpsContainerMapperTest {
    private val mapper: GpsContainerMapper = GpsContainerMapper()

    @Test
    fun `convert from proto to GpsContainer`() {
        val protoGpsContainer: ProtoGpsContainer =
            protoGpsContainer {
                name = "My Track"
                wayPoints +=
                    listOf(
                        protoWayPoint {
                            latitude = 1.0
                            longitude = 2.0
                            type = ProtoPoiType.SUMMIT
                        },
                    )
                track =
                    protoTrack {
                        wayPoints +=
                            protoWayPoint {
                                latitude = 1.0
                                longitude = 2.0
                            }
                    }
            }

        val domainGpsContainer = mapper.map(protoGpsContainer)

        assertThat(domainGpsContainer.name).isEqualTo("My Track")
        assertThat(domainGpsContainer.wayPoints).hasSize(1)
        assertThat(domainGpsContainer.wayPoints[0].type).isEqualTo(PoiType.SUMMIT)
        assertThat(domainGpsContainer.track!!.wayPoints).hasSize(1)
    }
}

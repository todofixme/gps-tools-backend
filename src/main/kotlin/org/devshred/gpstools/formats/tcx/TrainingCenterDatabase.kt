package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

const val TCX_NAMESPACE = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"

@JacksonXmlRootElement(
    localName = "TrainingCenterDatabase",
    namespace = TCX_NAMESPACE,
)
class TrainingCenterDatabase(
    @JacksonXmlElementWrapper(
        localName = "Courses",
        namespace = TCX_NAMESPACE,
    )
    @JacksonXmlProperty(localName = "Course")
    private val course: MutableList<Course> = mutableListOf(),
) {
    fun addCourse(course: Course) {
        this.course.add(course)
    }

    @JacksonXmlProperty(
        localName = "Course",
        namespace = TCX_NAMESPACE,
    )
    fun getCourse(): List<Course> {
        return course
    }
}

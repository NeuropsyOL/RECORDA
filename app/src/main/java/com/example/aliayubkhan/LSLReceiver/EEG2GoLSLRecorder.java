package com.example.aliayubkhan.LSLReceiver;

public class EEG2GoLSLRecorder

    {
        /// \brief Destructor.
        ///
        /// The destructor ensures that the object is properly uninitialized before it is destroyed.
        @Override
        protected void finalize() throws Throwable
        {
            StopRecording(); // Be sure all activity is stopped.

            super.finalize();
        }

        //--------------------------------------------------------------------------------------------//

        /// \brief Start recording of selected LSL streams to a file.
        ///
        /// Start recording of specific LSL streams \a strStreamNames to file \a strOutputFile.
        ///
        /// \note
        /// Due to an alleged bug in the LSL library, LSL streams cannot be properly resolved on
        /// Android. So recording multiple LSL streams to a single file will most likely not work as
        /// expected, i.e. not all specified streams will be found and recorded.
        ///
        /// \param[in] strOutputFile Path and name of the file to be saved. Be sure the specified path
        ///            does exist and is accessible.
        /// \param[in] strStreamNames An array of strings that identify the LSL streams to be recorded.
        ///            Streams can by identified by names and types. To record streams with a specific
        ///            name, use the string \code "NAME" \endcode. To record streams with a specific
        ///            name and type, use the string \code "NAME:TYPE" \endcode. Finally, to record
        ///            streams of a specific type, use the string \code ":TYPE" \endcode.
        ///
        /// \return This method is supposed to return \c true on success and \c false on failure.
        ///         However, capabilities of detecting failures are very limited.
        ///
        /// \see StopRecording()
        public boolean StartRecording(String strOutputFile, String[] strStreamNames)
        {
            StopRecording();

            mRecorderHandle = jniStartRecording(strOutputFile, strStreamNames);

            return 0 != mRecorderHandle;
        }

        //--------------------------------------------------------------------------------------------//

        /// \brief Stop a running recording process.
        ///
        /// Stop a currently running recording process.
        public void StopRecording()
        {
            if (0 != mRecorderHandle) { jniStopRecording(mRecorderHandle); mRecorderHandle=0; }
        }

        //--------------------------------------------------------------------------------------------//


        //--------------------------------------------------------------------------------------------//
        // Interface to the JNI recorder library (C implementation)
        //--------------------------------------------------------------------------------------------//

        /// \brief Load the LSL recorder library's JNI component.
        ///
        /// Load the LSL recorder library's JNI component. This must be done before the library can
        /// be used.
        static { System.loadLibrary("lslRecorderAndroid"); }

        /// \brief The internal JNI handle for the current class instance.
        ///
        /// The internal JNI handle for the current class instance. This is actually a plain C pointer,
        /// suck it up, Java! So the handle is only valid, if non-zero.
        private long mRecorderHandle = 0;

        //--------------------------------------------------------------------------------------------//

        /// \brief Start recording of selected LSL streams to a file.
        ///
        /// Start recording of specific LSL streams \a strStreamNames to file \a strOutputFile.
        ///
        /// \param[in] strOutputFile Path and name of the file to be saved. Be sure the specified path
        ///            does exist and is accessible.
        /// \param[in] strStreamNames An array of strings that identify the LSL streams to be recorded.
        ///            Streams can by identified by names and types. To record streams with a specific
        ///            name, use the string \code "NAME" \endcode. To record streams with a specific
        ///            name and type, use the string \code "NAME:TYPE" \endcode. Finally, to record
        ///            streams of a specific type, use the string \code ":TYPE" \endcode.
        @SuppressWarnings("JniMissingFunction")
        private static native long jniStartRecording(String strOutputFile, String[] strStreamNames);

        //--------------------------------------------------------------------------------------------//

        /// \brief Stop a running recording process.
        ///
        /// Stop a running recording process.
        @SuppressWarnings("JniMissingFunction")
        private static native long jniStopRecording(long iRecorderHandle);

        //--------------------------------------------------------------------------------------------//
    }
//------------------------------------------------------------------------------------------------//




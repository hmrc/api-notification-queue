#!/bin/bash

sbt coverage test it:test coverageOff coverageReport

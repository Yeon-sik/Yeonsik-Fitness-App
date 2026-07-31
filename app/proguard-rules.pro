# Project-specific R8 rules.
#
# The app currently uses platform APIs and org.json without reflection-based
# model serialization, so the optimized Android defaults are sufficient.
# Add narrowly scoped keep rules here if a future library requires reflection.

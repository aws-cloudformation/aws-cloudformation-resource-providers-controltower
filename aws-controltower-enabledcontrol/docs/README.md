# AWS::ControlTower::EnabledControl

Enables a control on a specified target.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::ControlTower::EnabledControl",
    "Properties" : {
        "<a href="#controlidentifier" title="ControlIdentifier">ControlIdentifier</a>" : <i>String</i>,
        "<a href="#targetidentifier" title="TargetIdentifier">TargetIdentifier</a>" : <i>String</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::ControlTower::EnabledControl
Properties:
    <a href="#controlidentifier" title="ControlIdentifier">ControlIdentifier</a>: <i>String</i>
    <a href="#targetidentifier" title="TargetIdentifier">TargetIdentifier</a>: <i>String</i>
</pre>

## Properties

#### ControlIdentifier

Arn of the control.

_Required_: Yes

_Type_: String

_Minimum_: <code>20</code>

_Maximum_: <code>2048</code>

_Pattern_: <code>^arn:aws[0-9a-zA-Z_\-:\/]+$</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### TargetIdentifier

Arn for Organizational unit to which the control needs to be applied

_Required_: Yes

_Type_: String

_Minimum_: <code>20</code>

_Maximum_: <code>2048</code>

_Pattern_: <code>^arn:aws[0-9a-zA-Z_\-:\/]+$</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

